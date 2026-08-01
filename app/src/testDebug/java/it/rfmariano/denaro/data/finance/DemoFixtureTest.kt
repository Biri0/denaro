package it.rfmariano.denaro.data.finance

import it.rfmariano.denaro.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class DemoFixtureTest {
    private val referenceDate = LocalDate.of(2026, 8, 1)
    private val zone = ZoneId.of("Europe/Rome")

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
                transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor } >
                    transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
            }

        assertEquals(expectedMonths, completed.map { YearMonth.from(LocalDate.parse(it.localDate)) }.toSet())
        assertEquals(2, deficits)
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
