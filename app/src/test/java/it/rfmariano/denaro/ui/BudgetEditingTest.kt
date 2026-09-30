package it.rfmariano.denaro.ui

import it.rfmariano.denaro.data.finance.BudgetSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BudgetEditingTest {

    @Test
    fun rowsListStoredBudgetsSortedByCurrency() {
        val rows = resolveBudgetRows(
            existing = listOf(budget("usd", "USD", 50_000), budget("eur", "EUR", 12_345)),
            edits = emptyMap(),
            digitsFor = { 2 },
        )
        assertEquals(listOf("EUR" to "123.45", "USD" to "500"), rows)
    }

    @Test
    fun rowsUseZeroDigitsForCurrenciesWithoutDecimals() {
        val rows = resolveBudgetRows(
            existing = listOf(budget("jpy", "JPY", 5_000)),
            edits = emptyMap(),
            digitsFor = { 0 },
        )
        assertEquals(listOf("JPY" to "5000"), rows)
    }

    @Test
    fun stagedAmountOverridesTheStoredOneWithoutDroppingOtherRows() {
        val rows = resolveBudgetRows(
            existing = listOf(budget("usd", "USD", 50_000), budget("eur", "EUR", 12_345)),
            edits = mapOf("USD" to "750.50"),
            digitsFor = { 2 },
        )
        assertEquals(listOf("EUR" to "123.45", "USD" to "750.50"), rows)
    }

    @Test
    fun stagedCurrencyAddsARowEvenWhenNoBudgetExistsYet() {
        val rows = resolveBudgetRows(
            existing = listOf(budget("usd", "USD", 50_000)),
            edits = mapOf("USD" to "500", "EUR" to "300"),
            digitsFor = { 2 },
        )
        assertEquals(listOf("EUR" to "300", "USD" to "500"), rows)
    }

    @Test
    fun clearedSlotDisappearsFromTheRows() {
        val rows = resolveBudgetRows(
            existing = listOf(budget("usd", "USD", 50_000), budget("eur", "EUR", 12_345)),
            edits = mapOf("USD" to ""),
            digitsFor = { 2 },
        )
        assertEquals(listOf("EUR" to "123.45"), rows)
    }

    @Test
    fun slotWithoutAmountHasNoRow() {
        val rows = resolveBudgetRows(
            existing = emptyList(),
            edits = mapOf("USD" to "   "),
            digitsFor = { 2 },
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun planConvertsTouchedAmountsToMinorUnits() {
        val plan = parseBudgetPlan(
            edits = mapOf("USD" to "500", "EUR" to "12.34"),
            digitsFor = { 2 },
        )
        assertEquals(mapOf("USD" to 50_000L, "EUR" to 1_234L), plan)
    }

    @Test
    fun planUsesTheScaleOfEachCurrency() {
        val plan = parseBudgetPlan(mapOf("JPY" to "5000")) { 0 }
        assertEquals(mapOf("JPY" to 5_000L), plan)
    }

    @Test
    fun planTreatsAClearedAmountAsDeletion() {
        val plan = parseBudgetPlan(mapOf("USD" to "  "), digitsFor = { 2 })
        assertEquals(mapOf("USD" to null), plan)
    }

    @Test
    fun planReportsOnlyTheCurrenciesTheUserTouched() {
        val plan = parseBudgetPlan(mapOf("EUR" to "10"), digitsFor = { 2 })
        assertEquals(setOf("EUR"), plan.keys)
    }

    @Test
    fun planRejectsInvalidAmountsBeforeAnythingIsWritten() {
        assertFails { parseBudgetPlan(mapOf("USD" to "abc"), digitsFor = { 2 }) }
        assertFails { parseBudgetPlan(mapOf("USD" to "1.2.3"), digitsFor = { 2 }) }
        assertFails { parseBudgetPlan(mapOf("USD" to "-5"), digitsFor = { 2 }) }
    }

    @Test
    fun planRejectsAnEmptyCurrency() {
        assertFails { parseBudgetPlan(mapOf("" to "500"), digitsFor = { 2 }) }
    }

    @Test
    fun stagedBlankForANewCurrencyNeverProducesARowOrAnAmount() {
        val edits = mapOf("GBP" to "")
        assertTrue(resolveBudgetRows(emptyList(), edits) { 2 }.isEmpty())
        assertEquals(mapOf("GBP" to null), parseBudgetPlan(edits) { 2 })
    }

    private fun assertFails(block: () -> Any?) {
        try {
            block()
            fail("expected an IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // The save path relies on this failing fast, before any database write.
        }
    }

    private fun budget(id: String, currency: String, amountMinor: Long) = BudgetSummary(
        id = id,
        categoryId = "fun",
        currency = currency,
        amountMinor = amountMinor,
        categoryName = "Fun",
        categoryIconName = "party_popper",
        categoryColorIndex = 1,
        categoryArchivedAt = null,
    )
}
