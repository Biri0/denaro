package it.rfmariano.denaro.data.migration

import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class LegacyTransformerTest {
    private val transformer = LegacyTransformer(
        zoneId = ZoneOffset.UTC,
        idGenerator = { timestamp -> "generated-$timestamp" },
    )

    @Test
    fun convertsSignedAmountsAndPreservesBalances() {
        val result = transformer.transform(
            snapshot(
                buckets = listOf(bucket("cash", openingBalance = 1_000)),
                transactions = listOf(
                    transaction("salary", "cash", amount = 2_500, date = 1_000),
                    transaction("lunch", "cash", amount = -750, date = 2_000),
                ),
            ),
        )

        assertEquals(
            listOf(TransactionType.INCOME, TransactionType.EXPENSE),
            result.transactions.map { it.type },
        )
        assertEquals(listOf(2_500L, 750L), result.transactions.map { it.amountMinor })
        assertEquals(2_750L, result.expectedBalances.getValue("cash"))
    }

    @Test
    fun convertsOnlyUniqueSameCurrencyTransferPair() {
        val result = transformer.transform(
            snapshot(
                buckets = listOf(bucket("checking"), bucket("cash")),
                transactions = listOf(
                    transaction(
                        id = "out",
                        bucketId = "checking",
                        amount = -500,
                        date = 10_000,
                        description = "ATM",
                    ),
                    transaction(
                        id = "in",
                        bucketId = "cash",
                        amount = 500,
                        date = 10_500,
                        description = " atm ",
                    ),
                ),
            ),
        )

        assertTrue(result.transactions.isEmpty())
        assertEquals(1, result.transfers.size)
        with(result.transfers.single()) {
            assertEquals("checking", fromAccountId)
            assertEquals("cash", toAccountId)
            assertEquals(500L, amountMinor)
            assertEquals("ATM", description)
        }
        assertEquals(-500L, result.expectedBalances.getValue("checking"))
        assertEquals(500L, result.expectedBalances.getValue("cash"))
    }

    @Test
    fun leavesAmbiguousTransferCandidatesAsTransactions() {
        val result = transformer.transform(
            snapshot(
                buckets = listOf(bucket("checking"), bucket("cash"), bucket("savings")),
                transactions = listOf(
                    transaction("out", "checking", -500, 10_000),
                    transaction("in-cash", "cash", 500, 10_100),
                    transaction("in-savings", "savings", 500, 10_200),
                ),
            ),
        )

        assertTrue(result.transfers.isEmpty())
        assertEquals(3, result.transactions.size)
    }

    @Test
    fun clampsMonthlyRecurrenceToEndOfMonth() {
        val startedAt = LocalDateTime.of(2024, 1, 31, 12, 30)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        val result = transformer.transform(
            snapshot(
                buckets = listOf(bucket("cash")),
                transactions = listOf(
                    transaction(
                        id = "monthly",
                        bucketId = "cash",
                        amount = -100,
                        date = startedAt,
                        intervalValue = 1,
                        intervalUnit = "months",
                        dayOfMonth = 31,
                    ),
                ),
            ),
        )

        val rule = result.recurringRules.single()
        assertEquals(RecurrenceFrequency.MONTHLY, rule.frequency)
        assertEquals(
            LocalDateTime.of(2024, 2, 29, 12, 30).toInstant(ZoneOffset.UTC).toEpochMilli(),
            rule.nextOccurrenceAt,
        )
        assertEquals(rule.id, result.transactions.single().recurringRuleId)
    }

    @Test
    fun disablesDevelopmentOnlySecondsRecurrence() {
        val result = transformer.transform(
            snapshot(
                buckets = listOf(bucket("cash")),
                transactions = listOf(
                    transaction(
                        id = "debug-rule",
                        bucketId = "cash",
                        amount = -100,
                        date = 1_000,
                        intervalValue = 5,
                        intervalUnit = "seconds",
                    ),
                ),
            ),
        )

        assertTrue(result.recurringRules.isEmpty())
        assertNull(result.transactions.single().recurringRuleId)
        assertEquals(
            listOf("Second-based recurrence debug-rule was disabled"),
            result.warnings,
        )
    }

    private fun snapshot(
        buckets: List<LegacyBucket>,
        transactions: List<LegacyTransaction>,
    ) = LegacySnapshot(buckets, transactions)

    private fun bucket(
        id: String,
        openingBalance: Long = 0,
        currency: String = "EUR",
    ) = LegacyBucket(
        id = id,
        title = id,
        description = null,
        initialBalanceMinor = openingBalance,
        currency = currency,
        createdAt = 0,
    )

    private fun transaction(
        id: String,
        bucketId: String,
        amount: Long,
        date: Long,
        description: String? = null,
        intervalValue: Int? = null,
        intervalUnit: String? = null,
        dayOfMonth: Int? = null,
    ) = LegacyTransaction(
        id = id,
        bucketId = bucketId,
        amountMinor = amount,
        description = description,
        date = date,
        intervalValue = intervalValue,
        intervalUnit = intervalUnit,
        dayOfMonth = dayOfMonth,
        month = null,
    )
}
