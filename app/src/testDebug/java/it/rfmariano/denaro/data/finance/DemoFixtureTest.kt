package it.rfmariano.denaro.data.finance

import it.rfmariano.denaro.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class DemoFixtureTest {
    private val referenceDate = LocalDate.of(2026, 8, 1)
    private val zone = ZoneId.of("Europe/Rome")

    /**
     * Expense totals per top-level category for one month, mirroring
     * BudgetDao.observeSpendByCategory, which rolls child spending up to the parent.
     */
    private fun DemoFixture.spendByRollup(month: YearMonth): Map<String, Long> {
        val categoryById = categories.associateBy { it.id }
        fun rollup(categoryId: String): String {
            val parentId = categoryById[categoryId]?.parentId
            return if (parentId == null) categoryId else rollup(parentId)
        }
        return transactions
            .filter { transaction ->
                transaction.type == TransactionType.EXPENSE &&
                    transaction.categoryId != null &&
                    YearMonth.from(LocalDate.parse(transaction.localDate)) == month
            }
            .groupBy { transaction -> rollup(requireNotNull(transaction.categoryId)) }
            .mapValues { (_, entries) -> entries.sumOf { it.amountMinor } }
    }

    @Test
    fun fixtureIsDeterministicAndReferentiallyValid() {
        val first = demoFixture(referenceDate, zone, italian = false)
        val second = demoFixture(referenceDate, zone, italian = false)

        assertEquals(first, second)
        val accountIds = first.accounts.mapTo(mutableSetOf()) { it.id }
        val categoryIds = first.categories.mapTo(mutableSetOf()) { it.id }
        assertTrue(first.transactions.all { it.accountId in accountIds })
        assertTrue(first.transactions.all { it.categoryId in categoryIds })
        assertTrue(first.transfers.all { it.fromAccountId in accountIds && it.toAccountId in accountIds })
        assertTrue(first.rules.all { it.accountId in accountIds && it.categoryId in categoryIds })
        assertTrue(first.categories.all { it.parentId == null || it.parentId in categoryIds })
        val counterpartyIds = first.counterparties.mapTo(mutableSetOf()) { it.id }
        val debtIds = first.debts.mapTo(mutableSetOf()) { it.id }
        assertTrue(first.debts.all { it.accountId in accountIds && it.counterpartyId in counterpartyIds })
        assertTrue(first.repayments.all { it.debtId in debtIds && it.accountId in accountIds })
    }

    @Test
    fun completedWindowContainsExactlyTwoDeficitMonths() {
        val fixture = demoFixture(referenceDate, zone, italian = false)
        val selected = YearMonth.from(referenceDate).minusMonths(1)
        val expectedMonths = (5L downTo 0L).map(selected::minusMonths).toSet()
        val completed = fixture.transactions.filter {
            YearMonth.from(LocalDate.parse(it.localDate)) in expectedMonths
        }
        val deficits = completed.groupBy { YearMonth.from(LocalDate.parse(it.localDate)) }
            .count { (_, transactions) ->
                transactions.filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amountMinor } >
                        transactions.filter { it.type == TransactionType.INCOME }
                            .sumOf { it.amountMinor }
            }

        assertEquals(
            expectedMonths,
            completed.map { YearMonth.from(LocalDate.parse(it.localDate)) }.toSet()
        )
        assertEquals(2, deficits)
    }

    @Test
    fun budgetsTargetExistingTopLevelCategoriesAndAreUniquePerCurrency() {
        val fixture = demoFixture(referenceDate, zone, italian = false)
        val categoryById = fixture.categories.associateBy { it.id }
        val currencies = fixture.accounts.mapTo(mutableSetOf()) { it.currency }

        assertTrue(fixture.budgets.isNotEmpty())
        assertEquals(
            fixture.budgets.size,
            fixture.budgets.map { it.categoryId to it.currency }.toSet().size,
        )
        fixture.budgets.forEach { budget ->
            val category = requireNotNull(categoryById[budget.categoryId])
            // FinanceRepository.validateBudget only accepts top-level expense categories, and
            // spend is rolled up to the parent, so a subcategory budget would read 0 forever.
            assertEquals(TransactionType.EXPENSE, category.type)
            assertNull(category.parentId)
            assertTrue(budget.currency in currencies)
            assertTrue(budget.amountMinor > 0)
        }
    }

    @Test
    fun everyBudgetIsUsedInTheShowcaseMonthAndOnlyLeisureExceedsIt() {
        val fixture = demoFixture(referenceDate, zone, italian = false)
        val spend = fixture.spendByRollup(YearMonth.from(referenceDate).minusMonths(1))
        val leisureId = fixture.categories.single { it.name == "Leisure" }.id

        fixture.budgets.forEach { budget ->
            val spent = spend[budget.categoryId] ?: 0L
            assertTrue("${budget.categoryId} spent nothing", spent > 0)
            if (budget.categoryId == leisureId) {
                assertTrue(
                    "Leisure should overshoot to show the over-budget state",
                    spent > budget.amountMinor,
                )
            } else {
                assertTrue(
                    "${budget.categoryId} spent $spent of ${budget.amountMinor}",
                    spent <= budget.amountMinor,
                )
            }
        }
    }

    @Test
    fun currentMonthCoversEveryBudgetOnceAllDaysHavePassed() {
        val fixture = demoFixture(LocalDate.of(2026, 8, 28), zone, italian = false)
        val spend = fixture.spendByRollup(YearMonth.of(2026, 8))

        fixture.budgets.forEach { budget ->
            assertTrue(
                "${budget.categoryId} has no current-month spend",
                (spend[budget.categoryId] ?: 0L) > 0,
            )
        }
    }

    @Test
    fun everyBrowsableMonthHasAFullyPopulatedSixMonthChart() {
        val fixture = demoFixture(referenceDate, zone, italian = false)
        val months = fixture.transactions
            .map { YearMonth.from(LocalDate.parse(it.localDate)) }
            .toSet()
        val openedOn = YearMonth.from(referenceDate).minusMonths(1)

        listOf(openedOn.minusMonths(1), openedOn, openedOn.plusMonths(1)).forEach { selected ->
            (5 downTo 0).forEach { offset ->
                val month = selected.minusMonths(offset.toLong())
                assertTrue("No transactions in $month", month in months)
            }
        }
    }

    @Test
    fun fixtureKeepsExactlyOneSavingsAccountWithATargetAboveItsBalance() {
        val savings = demoFixture(referenceDate, zone, italian = false)
            .accounts
            .filter { it.isSavings }

        assertEquals(1, savings.size)
        val account = savings.single()
        assertNotNull(account.savingsTargetMinor)
        assertTrue((account.savingsTargetMinor ?: 0) > account.openingBalanceMinor)
    }

    @Test
    fun fixtureUsesRequestedLanguageAndNeverCreatesFutureActivity() {
        val english = demoFixture(referenceDate, zone, italian = false)
        val italian = demoFixture(referenceDate, zone, italian = true)

        assertTrue(english.accounts.any { it.name == "Everyday" })
        assertTrue(italian.accounts.any { it.name == "Conto quotidiano" })
        assertTrue(english.categories.any { it.name == "Groceries" })
        assertTrue(italian.categories.any { it.name == "Spesa" })
        assertFalse(english.transactions.any { LocalDate.parse(it.localDate) > referenceDate })
        assertFalse(italian.transactions.any { LocalDate.parse(it.localDate) > referenceDate })
    }
}
