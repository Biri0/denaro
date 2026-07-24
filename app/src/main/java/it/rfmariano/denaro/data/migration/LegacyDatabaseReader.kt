package it.rfmariano.denaro.data.migration

import android.content.Context
import android.database.Cursor
import it.rfmariano.denaro.data.security.SqlCipherLoader
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

class LegacyDatabaseReader(
    context: Context,
    private val keyReader: LegacySecureStoreKeyReader =
        LegacySecureStoreKeyReader(context),
) {
    val databaseFile: File = File(context.filesDir, "SQLite/denaro.db")

    fun exists(): Boolean = databaseFile.isFile

    fun cleanupNeeded(): Boolean =
        legacyFiles().any(File::exists) || keyReader.hasDatabaseKeyEntries()

    fun readSnapshot(): LegacySnapshot {
        if (!exists()) {
            throw LegacyMigrationException("Legacy database was not found")
        }

        SqlCipherLoader.load()
        val database = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            keyReader.readDatabasePassphrase(),
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
        )
        return database.use {
            it.rawQuery("SELECT count(*) FROM sqlite_master", null).use(Cursor::moveToFirst)
            LegacySnapshot(
                buckets = readBuckets(it),
                transactions = readTransactions(it),
            )
        }
    }

    fun deleteLegacyFilesAndKey() {
        val failures = mutableListOf<String>()
        legacyFiles().forEach { file ->
            if (file.exists() && !file.delete()) {
                failures += file.name
            }
        }
        runCatching(keyReader::deleteDatabaseKeyEntries)
            .exceptionOrNull()
            ?.let { failures += it.message ?: "legacy database key" }
        if (failures.isNotEmpty()) {
            throw LegacyMigrationException(
                "Could not delete: ${failures.joinToString()}",
            )
        }
    }

    private fun legacyFiles(): List<File> = listOf(
        databaseFile,
        File("${databaseFile.absolutePath}-wal"),
        File("${databaseFile.absolutePath}-shm"),
        File("${databaseFile.absolutePath}-journal"),
    )

    private fun readBuckets(database: SQLiteDatabase): List<LegacyBucket> =
        database.rawQuery(
            """
            SELECT id, title, description, initial_balance, currency, created_at
            FROM buckets
            ORDER BY created_at, id
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LegacyBucket(
                            id = cursor.requiredString("id"),
                            title = cursor.requiredString("title"),
                            description = cursor.optionalString("description"),
                            initialBalanceMinor = cursor.requiredLong("initial_balance"),
                            currency = cursor.requiredString("currency"),
                            createdAt = cursor.requiredLong("created_at"),
                        ),
                    )
                }
            }
        }

    private fun readTransactions(database: SQLiteDatabase): List<LegacyTransaction> {
        val columns = database.rawQuery("PRAGMA table_info(transactions)", null).use {
            buildSet {
                while (it.moveToNext()) add(it.getString(it.getColumnIndexOrThrow("name")))
            }
        }
        val dayColumn = if ("day_of_month" in columns) "day_of_month" else "NULL"
        val monthColumn = if ("month" in columns) "month" else "NULL"
        return database.rawQuery(
            """
            SELECT id, bucket_id, amount, description, date,
                   interval_value, interval_unit,
                   $dayColumn AS day_of_month,
                   $monthColumn AS month
            FROM transactions
            ORDER BY date, id
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LegacyTransaction(
                            id = cursor.requiredString("id"),
                            bucketId = cursor.requiredString("bucket_id"),
                            amountMinor = cursor.requiredLong("amount"),
                            description = cursor.optionalString("description"),
                            date = cursor.requiredLong("date"),
                            intervalValue = cursor.optionalInt("interval_value"),
                            intervalUnit = cursor.optionalString("interval_unit"),
                            dayOfMonth = cursor.optionalInt("day_of_month"),
                            month = cursor.optionalInt("month"),
                        ),
                    )
                }
            }
        }
    }

    private fun Cursor.requiredString(column: String): String =
        getString(getColumnIndexOrThrow(column))
            ?: throw LegacyMigrationException("$column is null")

    private fun Cursor.optionalString(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.requiredLong(column: String): Long =
        getLong(getColumnIndexOrThrow(column))

    private fun Cursor.optionalInt(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }
}

class LegacyMigrationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
