package it.rfmariano.denaro.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.data.security.DatabaseKeyManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    @Test
    fun encryptedRoomDatabasePersistsAndCalculatesBalance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyManager = object : DatabaseKeyManager(context) {
            private val passphrase = ByteArray(32) { it.toByte() }
            override fun getOrCreatePassphrase(): ByteArray = passphrase.copyOf()
            override fun deleteKeyMaterial() = Unit
        }
        val factory = EncryptedDatabaseFactory(
            context = context,
            keyManager = keyManager,
            databaseName = TEST_DATABASE_NAME,
        )
        factory.deleteDatabaseAndKey()

        try {
            val database = factory.open()
            try {
                database.accountDao().insertAll(
                    listOf(
                        AccountEntity(
                            id = "account",
                            name = "Cash",
                            description = null,
                            openingBalanceMinor = 1_000,
                            currency = "EUR",
                            archivedAt = null,
                            createdAt = 1,
                            updatedAt = 1,
                        ),
                    ),
                )
                database.transactionDao().insertAll(
                    listOf(
                        TransactionEntity(
                            id = "expense",
                            accountId = "account",
                            recurringRuleId = null,
                            occurrenceKey = null,
                            amountMinor = 250,
                            type = TransactionType.EXPENSE,
                            occurredAt = 2,
                            localDate = "1970-01-01",
                            description = null,
                            createdAt = 2,
                            updatedAt = 2,
                        ),
                    ),
                )
            } finally {
                database.close()
            }

            val reopened = factory.open()
            try {
                assertEquals(
                    750L,
                    reopened.accountBalanceDao().getAll().single().balanceMinor,
                )
            } finally {
                reopened.close()
            }

            val databaseFile = context.getDatabasePath(
                TEST_DATABASE_NAME,
            )
            val header = ByteArray(SQLITE_HEADER.size)
            FileInputStream(databaseFile).use { input ->
                assertEquals(header.size, input.read(header))
            }
            assertFalse(header.contentEquals(SQLITE_HEADER))
        } finally {
            factory.deleteDatabaseAndKey()
        }
    }

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        const val TEST_DATABASE_NAME = "denaro-encryption-test.db"
    }
}
