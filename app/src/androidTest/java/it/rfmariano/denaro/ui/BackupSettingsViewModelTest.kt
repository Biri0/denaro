package it.rfmariano.denaro.ui

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.data.backup.BackupException
import it.rfmariano.denaro.data.backup.BackupInspection
import it.rfmariano.denaro.data.backup.BackupPreview
import it.rfmariano.denaro.data.backup.BackupProtection
import it.rfmariano.denaro.data.backup.BackupRecordCounts
import it.rfmariano.denaro.data.backup.DenaroBackupService
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import it.rfmariano.denaro.data.local.TransactionType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupSettingsViewModelTest {
    @Test
    fun stagedBackupCreationWritesValidBackupsAndCleansTemporaryFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        val cacheDirectory = File(context.cacheDir, "denaro-export-${System.nanoTime()}")
        assertTrue(cacheDirectory.mkdir())
        try {
            val service = DenaroBackupService(database, "2.0-test", cacheDirectory)
            val runner = ContentResolverBackupTaskRunner(
                service = service,
                contentResolver = context.contentResolver,
                cacheDirectory = cacheDirectory,
                processDueRecurrences = {},
            )
            listOf<CharArray?>(null, "secret".toCharArray()).forEachIndexed { index, password ->
                val destination =
                    File(context.cacheDir, "created-$index-${System.nanoTime()}.denaro")
                try {
                    runner.create(Uri.fromFile(destination), password)

                    val inspection = destination.inputStream().use { input ->
                        service.inspect(input, password)
                    }
                    assertEquals(0, inspection.preview.counts.total)
                    assertTrue(cacheDirectory.listFiles().orEmpty().isEmpty())
                } finally {
                    password?.fill('\u0000')
                    destination.delete()
                }
            }
        } finally {
            cacheDirectory.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun failedBackupGenerationDoesNotTruncateDestination() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        val cacheDirectory = File(context.cacheDir, "denaro-export-${System.nanoTime()}")
        val destination = File(context.cacheDir, "existing-${System.nanoTime()}.denaro")
        assertTrue(cacheDirectory.mkdir())
        destination.writeText("existing backup")
        try {
            val runner = ContentResolverBackupTaskRunner(
                service = DenaroBackupService(database, "2.0-test", cacheDirectory),
                contentResolver = context.contentResolver,
                cacheDirectory = cacheDirectory,
                processDueRecurrences = {},
            )

            val error = runCatching {
                runner.create(Uri.fromFile(destination), charArrayOf())
            }.exceptionOrNull()

            assertTrue(error is BackupException.EmptyPassword)
            assertEquals("existing backup", destination.readText())
            assertTrue(cacheDirectory.listFiles().orEmpty().isEmpty())
        } finally {
            destination.delete()
            cacheDirectory.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun destinationFailureDeletesStagedBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        val cacheDirectory = File(context.cacheDir, "denaro-export-${System.nanoTime()}")
        assertTrue(cacheDirectory.mkdir())
        try {
            val runner = ContentResolverBackupTaskRunner(
                service = DenaroBackupService(database, "2.0-test", cacheDirectory),
                contentResolver = context.contentResolver,
                cacheDirectory = cacheDirectory,
                processDueRecurrences = {},
            )

            val error = runCatching {
                runner.create(Uri.parse("content://missing.denaro.provider/backup"), null)
            }.exceptionOrNull()

            assertTrue(error != null)
            assertTrue(cacheDirectory.listFiles().orEmpty().isEmpty())
        } finally {
            cacheDirectory.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun restoreProcessesOverdueRecurrencesBeforeReturning() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        val backupFile = File.createTempFile("overdue-recurrence-", ".denaro", context.cacheDir)
        try {
            database.accountDao().insert(
                AccountEntity("account", "Cash", null, 0, "EUR", null, 1, 1),
            )
            database.recurringRuleDao().insert(
                RecurringRuleEntity(
                    id = "rule",
                    accountId = "account",
                    categoryId = null,
                    amountMinor = 100,
                    transactionType = TransactionType.EXPENSE,
                    description = "Daily expense",
                    frequency = RecurrenceFrequency.DAILY,
                    intervalCount = 1,
                    timezoneId = "UTC",
                    anchorDay = null,
                    anchorMonth = null,
                    startAt = DAY_MILLIS,
                    lastGeneratedAt = null,
                    nextOccurrenceAt = DAY_MILLIS,
                    isActive = true,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            )
            val service = DenaroBackupService(database, "2.0-test", now = { PROCESSING_TIME })
            val repository = FinanceRepository(database, clock = { PROCESSING_TIME })
            val backup = ByteArrayOutputStream().also { service.create(it, null) }.toByteArray()
            val inspection = service.inspect(backup.inputStream(), null)
            service.eraseFinanceData()
            backupFile.writeBytes(backup)
            val runner = ContentResolverBackupTaskRunner(
                service = service,
                contentResolver = context.contentResolver,
                cacheDirectory = context.cacheDir,
                processDueRecurrences = repository::processDueRecurrences,
            )

            runner.restore(Uri.fromFile(backupFile), null, inspection.contentDigest)

            assertEquals(3, database.transactionDao().observeAll().first().size)
            val restoredRule = requireNotNull(database.recurringRuleDao().getById("rule"))
            assertTrue(restoredRule.nextOccurrenceAt > PROCESSING_TIME)
        } finally {
            backupFile.delete()
            database.close()
        }
    }

    @Test
    fun createJobAndResultAreOwnedByViewModelState() = runBlocking {
        val runner = BlockingCreateRunner()
        val viewModel = BackupSettingsViewModel(runner)
        val password = "secret".toCharArray()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.create(TEST_URI, password)
        }
        withTimeout(5_000) { runner.started.await() }
        assertEquals(BackupOperation.CREATE, viewModel.uiState.value.operation)

        runner.release.complete(Unit)
        val completed = withTimeout(5_000) {
            viewModel.uiState.first { it.result == BackupResult.CREATED }
        }

        assertEquals(null, completed.operation)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun cancelingCreatePasswordDiscardsPendingExport() = runBlocking {
        val runner = RecordingDiscardRunner()
        val viewModel = BackupSettingsViewModel(runner)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.requestExportPassword(TEST_URI)
            viewModel.cancelPendingExport()
        }

        assertEquals(TEST_URI, withTimeout(5_000) { runner.discarded.await() })
        assertEquals(null, viewModel.uiState.value.dialog)
        assertEquals(null, viewModel.uiState.value.pendingUri)
    }

    @Test
    fun dismissingRestorePasswordDoesNotDiscardSourceDocument() = runBlocking {
        val runner = RecordingDiscardRunner(requirePassword = true)
        val viewModel = BackupSettingsViewModel(runner)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.inspect(TEST_URI, null)
        }
        withTimeout(5_000) {
            viewModel.uiState.first { it.dialog == BackupDialog.RESTORE_PASSWORD }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.dismissDialog()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(0, runner.discardCount)
        assertEquals(null, viewModel.uiState.value.dialog)
        assertEquals(null, viewModel.uiState.value.pendingUri)
    }

    @Test
    fun inspectedRestoreCandidateAndPasswordStayInViewModelState() = runBlocking {
        val runner = InspectRunner()
        val viewModel = BackupSettingsViewModel(runner)
        val password = "secret".toCharArray()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.inspect(TEST_URI, password)
        }
        val inspected = withTimeout(5_000) {
            viewModel.uiState.first { it.dialog == BackupDialog.RESTORE_CONFIRM }
        }

        assertEquals(TEST_URI, inspected.candidate?.uri)
        assertEquals("secret", inspected.candidate?.password?.concatToString())
        assertEquals("inspection-digest", inspected.candidate?.contentDigest)
        assertTrue(password.all { it == '\u0000' })

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.confirmRestore()
        }
        assertEquals("inspection-digest", withTimeout(5_000) { runner.restoredDigest.await() })
    }

    private class BlockingCreateRunner : BackupTaskRunner {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun create(uri: Uri, password: CharArray?) {
            started.complete(Unit)
            release.await()
        }

        override suspend fun discardExport(uri: Uri) = Unit
        override suspend fun inspect(uri: Uri, password: CharArray?) = inspection()
        override suspend fun restore(
            uri: Uri,
            password: CharArray?,
            expectedContentDigest: String,
        ) = Unit

        override suspend fun eraseFinanceData() = Unit
    }

    private class InspectRunner : BackupTaskRunner {
        val restoredDigest = CompletableDeferred<String>()

        override suspend fun create(uri: Uri, password: CharArray?) = Unit
        override suspend fun discardExport(uri: Uri) = Unit
        override suspend fun inspect(uri: Uri, password: CharArray?) = inspection()
        override suspend fun restore(
            uri: Uri,
            password: CharArray?,
            expectedContentDigest: String,
        ) {
            restoredDigest.complete(expectedContentDigest)
        }

        override suspend fun eraseFinanceData() = Unit
    }

    private class RecordingDiscardRunner(
        private val requirePassword: Boolean = false,
    ) : BackupTaskRunner {
        val discarded = CompletableDeferred<Uri>()
        var discardCount = 0
            private set

        override suspend fun create(uri: Uri, password: CharArray?) = Unit

        override suspend fun discardExport(uri: Uri) {
            discardCount += 1
            discarded.complete(uri)
        }

        override suspend fun inspect(uri: Uri, password: CharArray?): BackupInspection {
            if (requirePassword) throw BackupException.PasswordRequired()
            return inspection()
        }

        override suspend fun restore(
            uri: Uri,
            password: CharArray?,
            expectedContentDigest: String,
        ) = Unit

        override suspend fun eraseFinanceData() = Unit
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val PROCESSING_TIME = DAY_MILLIS * 3
        val TEST_URI: Uri = Uri.parse("content://backup/test.denaro")

        fun inspection() = BackupInspection(
            preview = BackupPreview(1, "2.0-test", BackupProtection.PASSWORD, counts(1)),
            currentCounts = counts(2),
            hasFinanceChanges = true,
            contentDigest = "inspection-digest",
        )

        fun counts(accounts: Int) = BackupRecordCounts(
            accounts = accounts,
            categories = 0,
            recurringRules = 0,
            transactions = 0,
            balanceAdjustments = 0,
            transfers = 0,
            counterparties = 0,
            debts = 0,
            debtRepayments = 0,
        )
    }
}
