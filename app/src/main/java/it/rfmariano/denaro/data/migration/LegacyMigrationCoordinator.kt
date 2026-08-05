package it.rfmariano.denaro.data.migration

import android.content.Context
import androidx.room.withTransaction
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.EncryptedDatabaseFactory
import it.rfmariano.denaro.data.local.LegacyImportEntity
import it.rfmariano.denaro.data.local.LegacyImportStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

sealed interface MigrationResult {
    data object NotNeeded : MigrationResult

    data class Success(
        val accountCount: Int,
        val transactionCount: Int,
        val transferCount: Int,
        val recurringRuleCount: Int,
        val warnings: List<String>,
    ) : MigrationResult

    data class Failure(val message: String) : MigrationResult
}

class LegacyMigrationCoordinator(
    private val context: Context,
    private val databaseFactory: EncryptedDatabaseFactory =
        EncryptedDatabaseFactory(context),
    private val legacyReader: LegacyDatabaseReader = LegacyDatabaseReader(context),
    private val transformer: LegacyTransformer = LegacyTransformer(),
) {
    suspend fun migrateIfNeeded(): MigrationResult = withContext(Dispatchers.IO) {
        when (legacyMigrationAction(legacyReader.exists())) {
            LegacyMigrationAction.CLEANUP_ONLY -> {
                retryLegacyCleanup()
                return@withContext MigrationResult.NotNeeded
            }

            LegacyMigrationAction.AUDIT_AND_MIGRATE -> Unit
        }

        val nativeDatabaseExisted = context.getDatabasePath(
            EncryptedDatabaseFactory.DATABASE_NAME,
        ).exists()
        var migrationWasPending = false
        var database: DenaroDatabase? = null
        try {
            database = databaseFactory.open()
            val existingImport = database.legacyImportDao().get(SOURCE)
            migrationWasPending =
                existingImport?.status == LegacyImportStatus.PENDING_VALIDATION

            if (existingImport?.status == LegacyImportStatus.COMPLETE) {
                database.close()
                database = null
                retryLegacyCleanup()
                return@withContext MigrationResult.NotNeeded
            }
            val snapshot = legacyReader.readSnapshot()
            val transformed = transformer.transform(snapshot)

            if (existingImport == null) {
                ensureEmpty(database)
                import(database, transformed)
            }
            database.close()
            database = databaseFactory.open()
            validate(database, snapshot, transformed)

            val pending = requireNotNull(database.legacyImportDao().get(SOURCE))
            database.legacyImportDao().update(
                pending.copy(status = LegacyImportStatus.COMPLETE),
            )
            database.close()
            database = null

            val cleanupWarning = runCatching {
                legacyReader.deleteLegacyFilesAndKey()
            }.exceptionOrNull()?.message
            val warnings = transformed.warnings +
                    listOfNotNull(cleanupWarning?.let { "Legacy cleanup pending: $it" })

            MigrationResult.Success(
                accountCount = transformed.accounts.size,
                transactionCount = transformed.transactions.size,
                transferCount = transformed.transfers.size,
                recurringRuleCount = transformed.recurringRules.size,
                warnings = warnings,
            )
        } catch (error: Exception) {
            database?.close()
            val completed = runCatching {
                val reopened = databaseFactory.open()
                try {
                    reopened.legacyImportDao().get(SOURCE)?.status ==
                            LegacyImportStatus.COMPLETE
                } finally {
                    reopened.close()
                }
            }.getOrDefault(false)
            if (!completed && (!nativeDatabaseExisted || migrationWasPending)) {
                runCatching(databaseFactory::deleteDatabaseAndKey)
            }
            MigrationResult.Failure(error.message ?: "Legacy migration failed")
        }
    }

    private suspend fun import(
        database: DenaroDatabase,
        transformed: TransformedLegacyData,
    ) {
        database.withTransaction {
            database.accountDao().insertAll(transformed.accounts)
            database.recurringRuleDao().insertAll(transformed.recurringRules)
            database.transactionDao().insertAll(transformed.transactions)
            database.transferDao().insertAll(transformed.transfers)
            database.legacyImportDao().upsert(
                LegacyImportEntity(
                    source = SOURCE,
                    status = LegacyImportStatus.PENDING_VALIDATION,
                    importedAt = System.currentTimeMillis(),
                    accountCount = transformed.accounts.size,
                    transactionCount = transformed.transactions.size,
                    transferCount = transformed.transfers.size,
                    recurringRuleCount = transformed.recurringRules.size,
                    warningsJson = JSONArray(transformed.warnings).toString(),
                ),
            )
        }
    }

    private suspend fun ensureEmpty(database: DenaroDatabase) {
        check(database.accountDao().count() == 0) {
            "Native database already contains accounts"
        }
        check(database.transactionDao().count() == 0) {
            "Native database already contains transactions"
        }
        check(database.transferDao().count() == 0) {
            "Native database already contains transfers"
        }
        check(database.recurringRuleDao().count() == 0) {
            "Native database already contains recurring rules"
        }
    }

    private suspend fun validate(
        database: DenaroDatabase,
        legacy: LegacySnapshot,
        transformed: TransformedLegacyData,
    ) {
        val record = database.legacyImportDao().get(SOURCE)
        check(record?.status == LegacyImportStatus.PENDING_VALIDATION) {
            "Migration audit record is missing"
        }
        check(database.accountDao().count() == transformed.accounts.size)
        check(database.transactionDao().count() == transformed.transactions.size)
        check(database.transferDao().count() == transformed.transfers.size)
        check(database.recurringRuleDao().count() == transformed.recurringRules.size)
        check(
            transformed.transactions.size + transformed.transfers.size * 2 ==
                    legacy.transactions.size,
        ) {
            "Not every legacy transaction was accounted for"
        }

        val actualBalances = database.accountBalanceDao().getAll().associate {
            it.accountId to it.balanceMinor
        }
        check(actualBalances == transformed.expectedBalances) {
            "Account balances changed during migration"
        }
        checkIntegrity(database)
    }

    private fun checkIntegrity(database: DenaroDatabase) {
        val sqlite = database.openHelper.writableDatabase
        sqlite.query("PRAGMA integrity_check").use {
            check(it.moveToFirst() && it.getString(0) == "ok") {
                "Native database integrity check failed"
            }
        }
        sqlite.query("PRAGMA foreign_key_check").use {
            check(!it.moveToFirst()) { "Native database foreign-key check failed" }
        }
    }

    private fun retryLegacyCleanup() {
        if (legacyReader.cleanupNeeded()) {
            runCatching(legacyReader::deleteLegacyFilesAndKey)
        }
    }

    private companion object {
        const val SOURCE = "expo_v1"
    }
}

internal enum class LegacyMigrationAction {
    CLEANUP_ONLY,
    AUDIT_AND_MIGRATE,
}

internal fun legacyMigrationAction(legacyDatabaseExists: Boolean): LegacyMigrationAction =
    if (legacyDatabaseExists) {
        LegacyMigrationAction.AUDIT_AND_MIGRATE
    } else {
        LegacyMigrationAction.CLEANUP_ONLY
    }
