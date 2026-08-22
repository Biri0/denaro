package it.rfmariano.denaro.data.backup

import android.util.Base64
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.BalanceAdjustmentEntity
import it.rfmariano.denaro.data.local.CategoryEntity
import it.rfmariano.denaro.data.local.CounterpartyEntity
import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.data.local.DebtEntity
import it.rfmariano.denaro.data.local.DebtRepaymentEntity
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.LegacyImportEntity
import it.rfmariano.denaro.data.local.LegacyImportStatus
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import it.rfmariano.denaro.data.local.TransactionEntity
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.data.local.TransferEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class DenaroBackupServiceTest {
    @Test
    fun initializationDeletesOnlyTemporaryFilesFromOlderProcesses() = runBlocking {
        withDatabase { database ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cacheDirectory = File(
                context.cacheDir,
                "denaro-backup-stale-${System.nanoTime()}",
            )
            assertTrue(cacheDirectory.mkdir())
            try {
                val oldRestore = File(cacheDirectory, "denaro-restore-old.tmp").apply {
                    writeText("decrypted")
                }
                val oldExport = File(cacheDirectory, "denaro-export-old.tmp").apply {
                    writeText("backup")
                }
                val unrelated = File(cacheDirectory, "unrelated.tmp").apply {
                    writeText("keep")
                }
                val currentProcess = BackupTemporaryFiles.createRestore(cacheDirectory)

                DenaroBackupService(database, "2.0-test", cacheDirectory)

                assertFalse(oldRestore.exists())
                assertFalse(oldExport.exists())
                assertTrue(unrelated.exists())
                assertTrue(currentProcess.exists())
            } finally {
                cacheDirectory.deleteRecursively()
            }
        }
    }

    @Test
    fun protectedBackupWithoutPasswordDoesNotReadStoredPayload() = runBlocking {
        withDatabase { database ->
            seed(database)
            val service = DenaroBackupService(database, "2.0-test")
            val backup = ByteArrayOutputStream().also {
                service.create(it, "secret".toCharArray())
            }.toByteArray()
            val magicSize = "DENARO_BACKUP\n".toByteArray(StandardCharsets.US_ASCII).size
            val headerLength = ByteBuffer.wrap(backup, magicSize, Int.SIZE_BYTES).int
            val headerEnd = magicSize + Int.SIZE_BYTES + headerLength
            val input = PayloadGuardInputStream(backup, headerEnd)

            val error = runCatching { service.inspect(input, null) }.exceptionOrNull()

            assertTrue(error is BackupException.PasswordRequired)
            assertEquals(headerEnd, input.bytesRead)
        }
    }

    @Test
    fun boundedPayloadStreamRejectsExpansionPastItsLimit() {
        val error = runCatching {
            LimitedInputStream(ByteArrayInputStream(ByteArray(33)), 32).use { it.readBytes() }
        }.exceptionOrNull()

        assertTrue(error is BackupException.InvalidBackup)
    }

    @Test
    fun streamedDecodingDeletesTemporaryFilesOnSuccessAndFailure() = runBlocking {
        withDatabase { database ->
            seed(database)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cacheDirectory = File(
                context.cacheDir,
                "denaro-backup-stream-${System.nanoTime()}",
            )
            assertTrue(cacheDirectory.mkdir())
            try {
                val service = DenaroBackupService(
                    database,
                    "2.0-test",
                    cacheDirectory,
                )
                val plain = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
                service.inspect(ByteArrayInputStream(plain), null)
                assertTrue(cacheDirectory.listFiles().orEmpty().isEmpty())

                val password = "stream-secret".toCharArray()
                val encrypted = ByteArrayOutputStream().also {
                    service.create(it, password)
                }.toByteArray()
                service.inspect(ByteArrayInputStream(encrypted), password)
                assertTrue(cacheDirectory.listFiles().orEmpty().isEmpty())

                val error = runCatching {
                    service.inspect(
                        ByteArrayInputStream(encrypted),
                        "wrong-password".toCharArray(),
                    )
                }.exceptionOrNull()
                assertTrue(error is BackupException.AuthenticationFailed)
                assertTrue(cacheDirectory.listFiles().orEmpty().isEmpty())
                password.fill('\u0000')
            } finally {
                cacheDirectory.delete()
            }
        }
    }

    @Test
    fun encryptedBackupRoundTripReplacesAllFinanceData() = runBlocking {
        withDatabase { database ->
            seed(database)
            val service = DenaroBackupService(database, "2.0-test", now = { 123_456L })
            val expected = snapshot(database)
            val password = "x".toCharArray()
            val output = ByteArrayOutputStream()

            val created = service.create(output, password)
            assertEquals(10, created.counts.total)
            assertEquals(BackupProtection.PASSWORD, created.protection)

            database.accountDao().insert(
                AccountEntity("extra", "Extra", null, 0, "EUR", null, 100, 100),
            )
            service.restore(
                ByteArrayInputStream(output.toByteArray()),
                password,
                output.toByteArray().contentDigest(),
                postRestoreInTransaction = {},
            )

            assertEquals(expected, snapshot(database))
            password.fill('\u0000')
        }
    }

    @Test
    fun wrongPasswordAndCorruptPlainBackupAreRejectedWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val service = DenaroBackupService(database, "2.0-test")
            val before = snapshot(database)
            val encrypted = ByteArrayOutputStream().also {
                service.create(it, "correct horse battery".toCharArray())
            }.toByteArray()

            val wrongPasswordError = runCatching {
                service.inspect(ByteArrayInputStream(encrypted), "incorrect password".toCharArray())
            }.exceptionOrNull()
            assertTrue(wrongPasswordError is BackupException.AuthenticationFailed)
            assertEquals(before, snapshot(database))

            val emptyPasswordError = runCatching {
                service.create(ByteArrayOutputStream(), charArrayOf())
            }.exceptionOrNull()
            assertTrue(emptyPasswordError is BackupException.EmptyPassword)

            val plain = ByteArrayOutputStream().also {
                service.create(it, null)
            }.toByteArray()
            plain[plain.lastIndex] = (plain.last().toInt() xor 1).toByte()
            val corruptBackupError = runCatching {
                service.inspect(ByteArrayInputStream(plain), null)
            }.exceptionOrNull()
            assertTrue(corruptBackupError is BackupException.InvalidBackup)
            assertEquals(before, snapshot(database))
        }
    }

    @Test
    fun inspectionDetectsExactMatchEmptyCurrentDataAndRealChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val service = DenaroBackupService(database, "2.0-test", now = { 123_456L })
            val backup = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()

            val matching = service.inspect(ByteArrayInputStream(backup), null)
            assertEquals(10, matching.currentCounts.total)
            assertTrue(!matching.hasFinanceChanges)

            database.accountDao().insert(
                AccountEntity("extra", "Extra", null, 0, "EUR", null, 100, 100),
            )
            val changed = service.inspect(ByteArrayInputStream(backup), null)
            assertEquals(11, changed.currentCounts.total)
            assertTrue(changed.hasFinanceChanges)

            service.eraseFinanceData()
            val empty = service.inspect(ByteArrayInputStream(backup), null)
            assertEquals(0, empty.currentCounts.total)
            assertTrue(empty.hasFinanceChanges)
        }
    }

    @Test
    fun recurringHistoryMayUseTheRulesPreviousAccount() = runBlocking {
        withDatabase { database ->
            seed(database)
            val ruleDao = database.recurringRuleDao()
            val rule = requireNotNull(ruleDao.getById("r1"))
            ruleDao.update(rule.copy(accountId = "a2", updatedAt = 20))
            val service = DenaroBackupService(database, "2.0-test")
            val backup = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()

            val inspection = service.inspect(ByteArrayInputStream(backup), null)
            service.eraseFinanceData()
            service.restore(ByteArrayInputStream(backup), null, inspection.contentDigest) {}

            assertEquals("a2", requireNotNull(ruleDao.getById("r1")).accountId)
            assertEquals(
                "a1",
                database.transactionDao().observeAll().first().single().accountId,
            )
        }
    }

    @Test
    fun restoreRejectsAFileChangedAfterInspectionWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val service = DenaroBackupService(database, "2.0-test")
            val inspectedBackup = ByteArrayOutputStream().also {
                service.create(it, null)
            }.toByteArray()
            val inspection = service.inspect(ByteArrayInputStream(inspectedBackup), null)

            database.accountDao().insert(
                AccountEntity("extra", "Extra", null, 0, "EUR", null, 100, 100),
            )
            val replacementBackup = ByteArrayOutputStream().also {
                service.create(it, null)
            }.toByteArray()
            val before = snapshot(database)

            val error = runCatching {
                service.restore(
                    ByteArrayInputStream(replacementBackup),
                    null,
                    inspection.contentDigest,
                    postRestoreInTransaction = {},
                )
            }.exceptionOrNull()

            assertTrue(error is BackupException.InvalidBackup)
            assertEquals(before, snapshot(database))
        }
    }

    @Test
    fun eraseFinanceDataDeletesRecordsButPreservesLegacyImportHistory() = runBlocking {
        withDatabase { database ->
            seed(database)
            val marker = LegacyImportEntity(
                source = "legacy-v1",
                status = LegacyImportStatus.COMPLETE,
                importedAt = 123,
                accountCount = 2,
                transactionCount = 1,
                transferCount = 1,
                recurringRuleCount = 1,
                warningsJson = "[]",
            )
            database.legacyImportDao().upsert(marker)

            DenaroBackupService(database, "2.0-test").eraseFinanceData()

            assertTrue(snapshot(database).isEmpty())
            assertEquals(marker, database.legacyImportDao().get(marker.source))
        }
    }

    @Test
    fun eraseFinanceDataRollsBackWhenAnyDeletionFails() = runBlocking {
        withDatabase { database ->
            seed(database)
            val before = snapshot(database)
            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER prevent_account_erase
                BEFORE DELETE ON accounts
                BEGIN
                    SELECT RAISE(ABORT, 'blocked for rollback test');
                END
                """.trimIndent(),
            )

            val error = runCatching {
                DenaroBackupService(database, "2.0-test").eraseFinanceData()
            }.exceptionOrNull()

            assertTrue(error != null)
            assertEquals(before, snapshot(database))
        }
    }

    @Test
    fun restoreRejectsInvalidRecurrenceAnchorsWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val service = DenaroBackupService(database, "2.0-test")
            val dao = database.recurringRuleDao()
            val validRule = requireNotNull(dao.getById("r1"))
            val yearlyRule = validRule.copy(
                frequency = RecurrenceFrequency.YEARLY,
                anchorDay = 10,
                anchorMonth = 8,
            )
            val invalidRules = listOf(
                "daily anchor day" to validRule.copy(
                    frequency = RecurrenceFrequency.DAILY,
                    anchorDay = 10,
                ),
                "weekly anchor month" to validRule.copy(
                    frequency = RecurrenceFrequency.WEEKLY,
                    anchorDay = null,
                    anchorMonth = 8,
                ),
                "monthly missing day" to validRule.copy(anchorDay = null),
                "monthly zero day" to validRule.copy(anchorDay = 0),
                "monthly day above range" to validRule.copy(anchorDay = 32),
                "monthly anchor month" to validRule.copy(anchorMonth = 8),
                "yearly missing day" to yearlyRule.copy(anchorDay = null),
                "yearly zero day" to yearlyRule.copy(anchorDay = 0),
                "yearly day above range" to yearlyRule.copy(anchorDay = 32),
                "yearly missing month" to yearlyRule.copy(anchorMonth = null),
                "yearly zero month" to yearlyRule.copy(anchorMonth = 0),
                "yearly month above range" to yearlyRule.copy(anchorMonth = 13),
            )

            invalidRules.forEach { (case, invalidRule) ->
                dao.update(invalidRule)
                val malformedBackup = ByteArrayOutputStream().also {
                    service.create(it, null)
                }.toByteArray()
                dao.update(validRule)
                val before = snapshot(database)

                val error = runCatching {
                    service.restore(
                        ByteArrayInputStream(malformedBackup),
                        null,
                        malformedBackup.contentDigest(),
                        postRestoreInTransaction = {},
                    )
                }.exceptionOrNull()

                assertTrue(case, error is BackupException.InvalidBackup)
                assertEquals(case, before, snapshot(database))
            }
        }
    }

    @Test
    fun restoreRejectsNegativeNextOccurrenceWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val service = DenaroBackupService(database, "2.0-test")
            val dao = database.recurringRuleDao()
            val validRule = requireNotNull(dao.getById("r1"))
            dao.update(validRule.copy(nextOccurrenceAt = Long.MIN_VALUE))
            val malformedBackup = ByteArrayOutputStream().also {
                service.create(it, null)
            }.toByteArray()
            dao.update(validRule)
            val before = snapshot(database)

            val inspectionError = runCatching {
                service.inspect(ByteArrayInputStream(malformedBackup), null)
            }.exceptionOrNull()
            assertTrue(inspectionError is BackupException.InvalidBackup)
            assertEquals(before, snapshot(database))

            assertRestoreRejectedWithoutChanges(database, service, malformedBackup)
        }
    }

    @Test
    fun inspectionAndRestoreRejectCategoryGrandchildrenWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val root = requireNotNull(database.categoryDao().getById("c1"))
            val child = root.copy(
                id = "c2",
                parentId = root.id,
                name = "Dining",
                createdAt = 20,
                updatedAt = 20,
            )
            val grandchild = child.copy(
                id = "c3",
                parentId = child.id,
                name = "Restaurants",
                createdAt = 21,
                updatedAt = 21,
            )
            database.categoryDao().insertAll(listOf(child, grandchild))
            val service = DenaroBackupService(database, "2.0-test")
            val malformed = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
            val before = snapshot(database)

            val inspectionError = runCatching {
                service.inspect(ByteArrayInputStream(malformed), null)
            }.exceptionOrNull()

            assertTrue(inspectionError is BackupException.InvalidBackup)
            assertEquals(before, snapshot(database))
            assertRestoreRejectedWithoutChanges(database, service, malformed)
        }
    }

    @Test
    fun inspectionAndRestoreRejectZeroValueFinanceRecordsWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val service = DenaroBackupService(database, "2.0-test")
            val rule = requireNotNull(database.recurringRuleDao().getById("r1"))
            val transaction = database.transactionDao().observeAll().first().single()
            val transfer = requireNotNull(database.transferDao().getById("x1"))
            val debt = requireNotNull(database.debtDao().getById("d1"))
            val cases = listOf<Pair<String, suspend () -> Unit>>(
                "recurring rule" to {
                    database.recurringRuleDao().update(rule.copy(amountMinor = 0))
                },
                "transaction" to {
                    database.transactionDao().update(transaction.copy(amountMinor = 0))
                },
                "transfer" to {
                    database.transferDao().update(transfer.copy(amountMinor = 0))
                },
                "debt" to {
                    database.debtDao().update(debt.copy(principalMinor = 0))
                },
            )
            val restoreValidRecords: suspend () -> Unit = {
                database.recurringRuleDao().update(rule)
                database.transactionDao().update(transaction)
                database.transferDao().update(transfer)
                database.debtDao().update(debt)
            }

            cases.forEach { (case, makeInvalid) ->
                makeInvalid()
                val malformed =
                    ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
                restoreValidRecords()
                val before = snapshot(database)

                val inspectionError = runCatching {
                    service.inspect(ByteArrayInputStream(malformed), null)
                }.exceptionOrNull()

                assertTrue(case, inspectionError is BackupException.InvalidBackup)
                assertEquals(case, before, snapshot(database))
                assertRestoreRejectedWithoutChanges(database, service, malformed)
            }
        }
    }

    @Test
    fun restoreRollsBackWhenPostRestoreProcessingFails() = runBlocking {
        withDatabase { database ->
            seed(database)
            val service = DenaroBackupService(database, "2.0-test")
            val backup = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
            database.accountDao().insert(
                AccountEntity("current", "Current", null, 0, "EUR", null, 30, 30),
            )
            val before = snapshot(database)

            val error = runCatching {
                service.restore(
                    ByteArrayInputStream(backup),
                    null,
                    backup.contentDigest(),
                ) {
                    database.withTransaction {
                        database.accountDao().insert(
                            AccountEntity("generated", "Generated", null, 0, "EUR", null, 31, 31),
                        )
                        error("post-restore failure")
                    }
                }
            }.exceptionOrNull()

            assertTrue(error != null)
            assertEquals(before, snapshot(database))
        }
    }

    @Test
    fun legacyCompatibleCurrencyBackupCanBeInspectedAndRestored() = runBlocking {
        withDatabase { database ->
            seed(database)
            val accountDao = database.accountDao()
            accountDao.update(requireNotNull(accountDao.getById("a1")).copy(currency = "XYZ"))
            accountDao.update(requireNotNull(accountDao.getById("a2")).copy(currency = "XYZ"))
            val debtDao = database.debtDao()
            debtDao.update(requireNotNull(debtDao.getById("d1")).copy(currency = "XYZ"))
            val service = DenaroBackupService(database, "2.0-test")
            val backup = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()

            service.inspect(ByteArrayInputStream(backup), null)
            service.eraseFinanceData()
            service.restore(ByteArrayInputStream(backup), null, backup.contentDigest()) {}

            assertEquals("XYZ", requireNotNull(accountDao.getById("a1")).currency)
            assertEquals("XYZ", requireNotNull(debtDao.getById("d1")).currency)
        }
    }

    @Test
    fun restoreRejectsCrossCurrencyTransferWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val accountDao = database.accountDao()
            val account = requireNotNull(accountDao.getById("a2"))
            accountDao.update(account.copy(currency = "USD"))
            val service = DenaroBackupService(database, "2.0-test")
            val malformed = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
            accountDao.update(account)

            assertRestoreRejectedWithoutChanges(database, service, malformed)
        }
    }

    @Test
    fun restoreRejectsDebtCurrencyDifferentFromItsAccountWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val debtDao = database.debtDao()
            val debt = requireNotNull(debtDao.getById("d1"))
            debtDao.update(debt.copy(currency = "USD"))
            val service = DenaroBackupService(database, "2.0-test")
            val malformed = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
            debtDao.update(debt)

            assertRestoreRejectedWithoutChanges(database, service, malformed)
        }
    }

    @Test
    fun restoreRejectsRepaymentsExceedingPrincipalWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val debtDao = database.debtDao()
            val original = requireNotNull(debtDao.getRepaymentById("q1"))
            val second = original.copy(id = "q2", amountMinor = 500, createdAt = 11, updatedAt = 11)
            debtDao.updateRepayment(original.copy(amountMinor = 600))
            debtDao.insertRepayment(second)
            val service = DenaroBackupService(database, "2.0-test")
            val malformed = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
            debtDao.deleteRepayment(second)
            debtDao.updateRepayment(original)

            assertRestoreRejectedWithoutChanges(database, service, malformed)
        }
    }

    @Test
    fun restoreRejectsOverflowingRepaymentTotalWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val debtDao = database.debtDao()
            val debt = requireNotNull(debtDao.getById("d1"))
            val original = requireNotNull(debtDao.getRepaymentById("q1"))
            val second = original.copy(id = "q2", amountMinor = 1, createdAt = 11, updatedAt = 11)
            debtDao.update(debt.copy(principalMinor = Long.MAX_VALUE))
            debtDao.updateRepayment(original.copy(amountMinor = Long.MAX_VALUE))
            debtDao.insertRepayment(second)
            val service = DenaroBackupService(database, "2.0-test")
            val malformed = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
            debtDao.deleteRepayment(second)
            debtDao.updateRepayment(original)
            debtDao.update(debt)

            assertRestoreRejectedWithoutChanges(database, service, malformed)
        }
    }

    @Test
    fun restoreRejectsRepaymentBeforeDebtOpeningWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            val debtDao = database.debtDao()
            val original = requireNotNull(debtDao.getRepaymentById("q1"))
            debtDao.updateRepayment(original.copy(occurredAt = 8, localDate = "2026-08-12"))
            val service = DenaroBackupService(database, "2.0-test")
            val malformed = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
            debtDao.updateRepayment(original)

            assertRestoreRejectedWithoutChanges(database, service, malformed)
        }
    }

    @Test
    fun restoreRejectsRepaymentAccountWithDifferentCurrencyWithoutChanges() = runBlocking {
        withDatabase { database ->
            seed(database)
            database.accountDao().insert(
                AccountEntity("a3", "Dollars", null, 0, "USD", null, 12, 12),
            )
            val debtDao = database.debtDao()
            val original = requireNotNull(debtDao.getRepaymentById("q1"))
            debtDao.updateRepayment(original.copy(accountId = "a3"))
            val service = DenaroBackupService(database, "2.0-test")
            val malformed = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
            debtDao.updateRepayment(original)

            assertRestoreRejectedWithoutChanges(database, service, malformed)
        }
    }

    private suspend fun seed(database: DenaroDatabase) {
        val account1 = AccountEntity("a1", "Cash", "Primary", 10_000, "EUR", null, 1, 1)
        val account2 = AccountEntity("a2", "Card", null, 2_000, "EUR", 99, 2, 99)
        database.accountDao().insertAll(listOf(account1, account2))
        database.categoryDao().insertAll(
            listOf(
                CategoryEntity(
                    "c1",
                    TransactionType.EXPENSE,
                    null,
                    "Food",
                    "utensils",
                    2,
                    null,
                    null,
                    3,
                    3
                )
            ),
        )
        database.backupDao().insertCounterparties(
            listOf(CounterpartyEntity("p1", "Alex", "Friend", null, 4, 4)),
        )
        database.recurringRuleDao().insertAll(
            listOf(
                RecurringRuleEntity(
                    "r1",
                    "a1",
                    "c1",
                    500,
                    TransactionType.EXPENSE,
                    "Lunch",
                    RecurrenceFrequency.MONTHLY,
                    1,
                    "Europe/Rome",
                    10,
                    null,
                    5,
                    null,
                    6,
                    true,
                    5,
                    5
                )
            ),
        )
        database.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    "t1",
                    "a1",
                    "r1",
                    "c1",
                    "2026-08",
                    500,
                    TransactionType.EXPENSE,
                    6,
                    "2026-08-10",
                    "Lunch",
                    6,
                    6
                )
            ),
        )
        database.backupDao().insertBalanceAdjustments(
            listOf(BalanceAdjustmentEntity("b1", "a1", 100, 9_500, 9_600, 7, "2026-08-11", 7)),
        )
        database.transferDao().insertAll(
            listOf(TransferEntity("x1", "a1", "a2", 250, 8, "2026-08-12", "Move", 8, 8)),
        )
        database.backupDao().insertDebts(
            listOf(
                DebtEntity(
                    "d1",
                    "p1",
                    "a1",
                    DebtDirection.LENT,
                    1_000,
                    "EUR",
                    9,
                    "2026-08-13",
                    "2026-09-01",
                    "Loan",
                    9,
                    9
                )
            ),
        )
        database.backupDao().insertDebtRepayments(
            listOf(DebtRepaymentEntity("q1", "d1", "a1", 100, 10, "2026-08-14", "Part", 10, 10)),
        )
    }

    private suspend fun snapshot(database: DenaroDatabase): List<Any> = database.backupDao().run {
        accounts() + categories() + recurringRules() + transactions() + balanceAdjustments() +
                transfers() + counterparties() + debts() + debtRepayments()
    }

    private suspend fun assertRestoreRejectedWithoutChanges(
        database: DenaroDatabase,
        service: DenaroBackupService,
        malformed: ByteArray,
    ) {
        val before = snapshot(database)
        val error = runCatching {
            service.restore(
                ByteArrayInputStream(malformed),
                null,
                malformed.contentDigest(),
                postRestoreInTransaction = {},
            )
        }.exceptionOrNull()
        assertTrue(error is BackupException.InvalidBackup)
        assertEquals(before, snapshot(database))
    }

    private suspend fun withDatabase(block: suspend (DenaroDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun ByteArray.contentDigest(): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(this),
        Base64.NO_WRAP,
    )

    private class PayloadGuardInputStream(
        private val source: ByteArray,
        private val payloadOffset: Int,
    ) : InputStream() {
        var bytesRead: Int = 0
            private set

        override fun read(): Int {
            check(bytesRead < payloadOffset) { "Stored payload was read" }
            return source[bytesRead++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            buffer[offset] = read().toByte()
            return 1
        }
    }
}
