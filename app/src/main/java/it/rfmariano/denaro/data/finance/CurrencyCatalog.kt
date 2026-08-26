package it.rfmariano.denaro.data.finance

import java.util.Currency
import java.util.Date
import java.util.Locale
import android.icu.util.Currency as IcuCurrency

data class CurrencyEntry(
    val code: String,
    val displayName: String,
)

object CurrencyCatalog {
    val SeedCurrencies = listOf("EUR", "USD", "GBP")

    const val MAX_PREFERRED_ENTRIES = 6

    private val SpecialPurposeCurrencies = setOf(
        "BOV", "CHE", "CHW", "CLF", "COU", "MXV", "USN", "USS", "UYI",
        "XAD", "XAG", "XAU", "XBA", "XBB", "XBC", "XBD", "XDR", "XFO", "XFU",
        "XPD", "XPT", "XSU", "XTS", "XUA", "XXX",
    )

    fun localeCurrencyCode(locale: Locale = Locale.getDefault()): String? =
        runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()

    fun automaticDefault(
        usedCurrencies: Collection<String>,
        locale: Locale = Locale.getDefault(),
        includeAll: Boolean = false,
    ): String {
        val availableCodes = entries(locale, includeAll).mapTo(mutableSetOf()) { it.code }
        return preferredCodes(usedCurrencies, locale).firstOrNull { it in availableCodes }
            ?: availableCodes.firstOrNull()
            ?: SeedCurrencies.first()
    }

    fun preferredCodes(
        usedCurrencies: Collection<String>,
        locale: Locale = Locale.getDefault(),
    ): List<String> {
        val preferred = LinkedHashSet<String>()
        usedCurrencies
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .forEach { preferred.add(it.key) }
        localeCurrencyCode(locale)?.let { preferred.add(it) }
        preferred.addAll(SeedCurrencies)
        return preferred.toList().take(MAX_PREFERRED_ENTRIES)
    }

    fun entries(
        locale: Locale = Locale.getDefault(),
        includeAll: Boolean = false,
        preferredCodes: List<String> = emptyList(),
    ): List<CurrencyEntry> {
        val today = Date()
        val all = Currency.getAvailableCurrencies()
            .asSequence()
            .filter {
                includeAll || (
                        it.currencyCode !in SpecialPurposeCurrencies &&
                                IcuCurrency.isAvailable(it.currencyCode, today, today)
                        )
            }
            .map { CurrencyEntry(it.currencyCode, it.getDisplayName(locale)) }
            .toList()
        val byCode = all.associateBy { it.code }
        val preferred = preferredCodes.mapNotNull(byCode::get)
        val rest = all.asSequence()
            .filter { it.code !in preferredCodes.toSet() }
            .sortedWith(compareBy({ it.displayName.lowercase(locale) }, { it.code }))
            .toList()
        return preferred + rest
    }

    fun isValid(code: String): Boolean =
        code.length == 3 &&
                code == code.uppercase(Locale.ROOT) &&
                runCatching { Currency.getInstance(code) }.isSuccess

    fun matches(entry: CurrencyEntry, query: String): Boolean =
        query.isBlank() ||
                entry.code.contains(query, ignoreCase = true) ||
                entry.displayName.contains(query, ignoreCase = true)
}
