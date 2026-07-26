package it.rfmariano.denaro.data.finance

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.data.local.DenaroDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceRepositoryTest {
    @Test
    fun transferCreateAndUpdateRejectNegativeTimestamps() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DenaroDatabase::class.java,
        ).build()

        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val sourceId = repository.createAccount(accountInput("Source"))
            val destinationId = repository.createAccount(accountInput("Destination"))
            val validTransferId = repository.createTransfer(
                transferInput(sourceId, destinationId, occurredAt = 1),
            )

            val createFailure = runCatching {
                repository.createTransfer(
                    transferInput(sourceId, destinationId, occurredAt = -1),
                )
            }.exceptionOrNull()
            assertTrue(createFailure is IllegalArgumentException)
            assertEquals("Date is invalid", createFailure?.message)
            assertEquals(1, database.transferDao().count())

            val updateFailure = runCatching {
                repository.updateTransfer(
                    validTransferId,
                    transferInput(sourceId, destinationId, occurredAt = -1),
                )
            }.exceptionOrNull()
            assertTrue(updateFailure is IllegalArgumentException)
            assertEquals("Date is invalid", updateFailure?.message)
            assertEquals(1L, database.transferDao().getById(validTransferId)?.occurredAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun transferSuggestionsExcludeArchivedAndIncompatibleAccounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DenaroDatabase::class.java,
        ).build()

        try {
            val repository = FinanceRepository(database, clock = { 10 })
            val alphaId = repository.createAccount(accountInput("Alpha"))
            val betaId = repository.createAccount(accountInput("Beta"))
            val archivedId = repository.createAccount(accountInput("Archived"))
            repository.createAccount(accountInput("Yen", currency = "JPY"))
            repeat(2) {
                repository.createTransfer(
                    transferInput(alphaId, betaId, occurredAt = it.toLong() + 1),
                )
            }
            repeat(3) {
                repository.createTransfer(
                    transferInput(archivedId, betaId, occurredAt = it.toLong() + 10),
                )
            }
            repository.archiveAccount(archivedId)

            val suggestions = repository.getTransferAccountSuggestions()

            assertEquals(alphaId, suggestions.preferredSourceId)
            assertEquals(betaId, suggestions.preferredDestinationIds[alphaId])
            assertEquals(null, suggestions.preferredDestinationIds[archivedId])
        } finally {
            database.close()
        }
    }

    private fun accountInput(
        name: String,
        currency: String = "EUR",
    ) = AccountInput(
        name = name,
        description = null,
        openingBalanceMinor = 0,
        currency = currency,
    )

    private fun transferInput(
        sourceId: String,
        destinationId: String,
        occurredAt: Long,
    ) = TransferInput(
        fromAccountId = sourceId,
        toAccountId = destinationId,
        amountMinor = 100,
        occurredAt = occurredAt,
        description = null,
    )
}
