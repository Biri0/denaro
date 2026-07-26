package it.rfmariano.denaro.data.finance

import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.TransferPairUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferAccountSuggestionsTest {
    @Test
    fun ranksSourcesAndDirectionalDestinationsByFrequencyThenRecency() {
        val accounts = listOf(
            account("alpha", "Alpha"),
            account("beta", "Beta"),
            account("gamma", "Gamma"),
        )
        val suggestions = buildTransferAccountSuggestions(
            accounts = accounts,
            usage = listOf(
                usage("alpha", "gamma", count = 2, last = 30),
                usage("beta", "alpha", count = 2, last = 50),
                usage("beta", "gamma", count = 1, last = 60),
                usage("gamma", "alpha", count = 3, last = 40),
            ),
        )

        assertEquals("beta", suggestions.preferredSourceId)
        assertEquals("alpha", suggestions.preferredDestinationIds["beta"])
        assertEquals("gamma", suggestions.preferredDestinationIds["alpha"])
    }

    @Test
    fun sourceRecencyBreaksEqualUsageCounts() {
        val suggestions = buildTransferAccountSuggestions(
            accounts = listOf(
                account("alpha", "Alpha"),
                account("beta", "Beta"),
                account("gamma", "Gamma"),
            ),
            usage = listOf(
                usage("alpha", "gamma", count = 2, last = 10),
                usage("beta", "gamma", count = 2, last = 20),
            ),
        )

        assertEquals("beta", suggestions.preferredSourceId)
    }

    @Test
    fun fallsBackAlphabeticallyAndSelectsOnlySoleCompatibleDestination() {
        val twoAccounts = buildTransferAccountSuggestions(
            accounts = listOf(
                account("zulu", "Zulu"),
                account("alpha", "Alpha"),
                account("yen", "Yen", currency = "JPY"),
            ),
            usage = emptyList(),
        )

        assertEquals("alpha", twoAccounts.preferredSourceId)
        assertEquals("zulu", twoAccounts.preferredDestinationIds["alpha"])
        assertNull(twoAccounts.preferredDestinationIds["yen"])

        val multipleDestinations = buildTransferAccountSuggestions(
            accounts = listOf(
                account("alpha", "Alpha"),
                account("beta", "Beta"),
                account("gamma", "Gamma"),
            ),
            usage = emptyList(),
        )
        assertEquals("alpha", multipleDestinations.preferredSourceId)
        assertNull(multipleDestinations.preferredDestinationIds["alpha"])
    }

    private fun account(
        id: String,
        name: String,
        currency: String = "EUR",
    ) = AccountEntity(
        id = id,
        name = name,
        description = null,
        openingBalanceMinor = 0,
        currency = currency,
        archivedAt = null,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun usage(
        from: String,
        to: String,
        count: Long,
        last: Long,
    ) = TransferPairUsage(
        fromAccountId = from,
        toAccountId = to,
        transferCount = count,
        lastOccurredAt = last,
    )
}
