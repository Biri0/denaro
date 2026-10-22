package it.rfmariano.denaro.ui

import it.rfmariano.denaro.data.finance.DebtSummary
import it.rfmariano.denaro.data.local.DebtDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtFilteringTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test
    fun filtersByDirectionAndStatus() {
        val debts = listOf(
            debt("a1", "alice", "Alice", DebtDirection.LENT, 100_00L),
            debt("a2", "alice", "Alice", DebtDirection.BORROWED, 40_00L),
            debt("a3", "alice", "Alice", DebtDirection.LENT, 10_00L, repaid = 10_00L),
            debt("b1", "bob", "Bob", DebtDirection.LENT, 25_00L),
        )

        assertEquals(
            listOf("a1", "a2", "b1"),
            filterDebts(debts, null, DebtStatusFilter.OPEN, today).map(DebtSummary::id),
        )
        assertEquals(
            listOf("a1", "b1"),
            filterDebts(
                debts,
                DebtDirection.LENT,
                DebtStatusFilter.OPEN,
                today
            ).map(DebtSummary::id),
        )
        assertEquals(
            listOf("a2"),
            filterDebts(
                debts,
                DebtDirection.BORROWED,
                DebtStatusFilter.OPEN,
                today
            ).map(DebtSummary::id),
        )
        assertEquals(
            listOf("a3"),
            filterDebts(debts, null, DebtStatusFilter.SETTLED, today).map(DebtSummary::id),
        )
        assertTrue(
            filterDebts(
                debts,
                DebtDirection.BORROWED,
                DebtStatusFilter.SETTLED,
                today
            ).isEmpty()
        )
    }

    @Test
    fun filtersByCounterparty() {
        val debts = listOf(
            debt("a1", "alice", "Alice", DebtDirection.LENT, 100_00L),
            debt("a2", "alice", "Alice", DebtDirection.BORROWED, 40_00L),
            debt("b1", "bob", "Bob", DebtDirection.LENT, 25_00L),
        )

        assertEquals(
            listOf("a1", "a2"),
            filterDebts(debts, null, DebtStatusFilter.OPEN, today, "alice").map(DebtSummary::id),
        )
        assertEquals(
            listOf("b1"),
            filterDebts(debts, null, DebtStatusFilter.OPEN, today, "bob").map(DebtSummary::id),
        )
    }

    @Test
    fun sortsOverdueDebtsFirst() {
        val debts = listOf(
            debt("a1", "alice", "Alice", DebtDirection.LENT, 100_00L),
            debt("b1", "bob", "Bob", DebtDirection.LENT, 100_00L, dueDate = "2026-10-20"),
        )

        assertEquals(
            listOf("b1", "a1"),
            filterDebts(debts, null, DebtStatusFilter.OPEN, today).map(DebtSummary::id),
        )
    }

    @Test
    fun filterOptionsAreDistinctAndSortedByName() {
        val debts = listOf(
            debt("a1", "alice", "Alice", DebtDirection.LENT, 100_00L),
            debt("b1", "bob", "Bob", DebtDirection.LENT, 25_00L),
            debt("b2", "bob", "Bob", DebtDirection.BORROWED, 10_00L),
        )

        assertEquals(
            listOf(
                CounterpartyFilterOption("alice", "Alice"),
                CounterpartyFilterOption("bob", "Bob")
            ),
            counterpartyFilterOptions(debts),
        )
    }

    private fun debt(
        id: String,
        counterpartyId: String,
        counterpartyName: String,
        direction: DebtDirection,
        principalMinor: Long,
        repaid: Long = 0L,
        currency: String = "USD",
        dueDate: String? = null,
    ) = DebtSummary(
        id = id,
        counterpartyId = counterpartyId,
        counterpartyName = counterpartyName,
        accountId = "account",
        accountName = "Account",
        direction = direction,
        principalMinor = principalMinor,
        repaidMinor = repaid,
        currency = currency,
        openedAt = 0L,
        localDate = "2026-01-01",
        dueDate = dueDate,
        note = null,
        fractionDigits = 2,
    )
}
