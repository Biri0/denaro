package it.rfmariano.denaro.data.finance

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Currency
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CurrencyCatalogTest {
    @Test
    fun nonCurrentCurrenciesAreHiddenByDefaultAndVisibleWhenRequested() {
        val defaultCodes = CurrencyCatalog.entries(Locale.US).map { it.code }
        assertFalse(defaultCodes.contains("ITL"))
        assertFalse(defaultCodes.contains("TRL"))
        assertFalse(defaultCodes.contains("USS"))
        assertFalse(defaultCodes.contains("XXX"))
        assertFalse(defaultCodes.contains("XPT"))

        val allCodes = CurrencyCatalog.entries(Locale.US, includeAll = true).map { it.code }
        assertTrue(allCodes.contains("ITL"))
        assertTrue(allCodes.contains("USS"))
        assertTrue(allCodes.contains("XPT"))
    }

    @Test
    fun activeCurrenciesRemainAvailableByDefault() {
        val codes = CurrencyCatalog.entries(Locale.US).map { it.code }
        assertTrue(codes.containsAll(listOf("EUR", "USD", "CHF")))
        // XCG is only present on devices whose ICU table knows it; older supported
        // devices legitimately omit it from the catalog.
        if (runCatching { Currency.getInstance("XCG") }.isSuccess) {
            assertTrue(codes.contains("XCG"))
        }
    }

    @Test
    fun preferredEntriesComeFirstAndTailIsSortedByName() {
        val entries = CurrencyCatalog.entries(
            locale = Locale.US,
            preferredCodes = listOf("CHF", "EUR"),
        )
        assertEquals(listOf("CHF", "EUR"), entries.take(2).map { it.code })
        val tail = entries.drop(2)
        val sorted =
            tail.sortedWith(compareBy({ it.displayName.lowercase(Locale.US) }, { it.code }))
        assertEquals(sorted, tail)
        assertEquals(tail.size, tail.map { it.code }.distinct().size)
        assertTrue(tail.none { it.code in setOf("CHF", "EUR") })
    }

    @Test
    fun preferredCodesRankByUsageThenLocaleThenSeeds() {
        val codes = CurrencyCatalog.preferredCodes(
            usedCurrencies = listOf("USD", "JPY", "JPY", "EUR"),
            locale = Locale.US,
        )
        assertEquals(listOf("JPY", "EUR", "USD", "GBP"), codes)
    }

    @Test
    fun freshInstallFallsBackToLocaleAndSeeds() {
        val codes = CurrencyCatalog.preferredCodes(emptyList(), Locale.US)
        assertEquals(listOf("USD", "EUR", "GBP"), codes)
    }

    @Test
    fun localeCurrencyCodeResolvesOnlyCountrySpecificLocales() {
        assertEquals("USD", CurrencyCatalog.localeCurrencyCode(Locale.US))
        assertNull(CurrencyCatalog.localeCurrencyCode(Locale.ROOT))
    }

    @Test
    fun automaticDefaultPrefersMostUsedThenBreaksTiesAlphabetically() {
        val used = listOf("EUR", "EUR", "EUR", "KHR")
        assertEquals("EUR", CurrencyCatalog.automaticDefault(used, Locale.US))
        assertEquals("EUR", CurrencyCatalog.automaticDefault(listOf("KHR", "EUR"), Locale.US))
    }

    @Test
    fun automaticDefaultSkipsHiddenUsedCurrencies() {
        assertEquals("USD", CurrencyCatalog.automaticDefault(listOf("ITL"), Locale.US))
        assertEquals("ITL", CurrencyCatalog.automaticDefault(listOf("ITL"), Locale.US, true))
    }

    @Test
    fun automaticDefaultFallsBackToLocaleThenSeed() {
        assertEquals("USD", CurrencyCatalog.automaticDefault(emptyList(), Locale.US))
        assertEquals("EUR", CurrencyCatalog.automaticDefault(emptyList(), Locale.ROOT))
    }

    @Test
    fun preferredCodesNeverExceedTheLimit() {
        val codes = CurrencyCatalog.preferredCodes(
            usedCurrencies = listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF", "GGG"),
        )
        assertEquals(CurrencyCatalog.MAX_PREFERRED_ENTRIES, codes.size)
    }

    @Test
    fun isValidAcceptsIsoCodesAndRejectsEverythingElse() {
        assertTrue(CurrencyCatalog.isValid("EUR"))
        assertTrue(CurrencyCatalog.isValid("JPY"))
        assertTrue(CurrencyCatalog.isValid("ITL"))
        assertFalse(CurrencyCatalog.isValid("eur"))
        assertFalse(CurrencyCatalog.isValid("EURO"))
        assertFalse(CurrencyCatalog.isValid(""))
        assertFalse(CurrencyCatalog.isValid("EU1"))
    }

    @Test
    fun matchesQueriesCodeAndDisplayNameCaseInsensitively() {
        val entry = CurrencyEntry("CHF", "Swiss Franc")
        assertTrue(CurrencyCatalog.matches(entry, ""))
        assertTrue(CurrencyCatalog.matches(entry, "ch"))
        assertTrue(CurrencyCatalog.matches(entry, "franc"))
        assertFalse(CurrencyCatalog.matches(entry, "yen"))
    }

    @Test
    fun everyEntryHasNonBlankCode() {
        CurrencyCatalog.entries(Locale.US).forEach { entry ->
            assertTrue(entry.code.isNotBlank())
            assertTrue(entry.displayName.isNotBlank())
        }
    }
}
