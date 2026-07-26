package it.rfmariano.denaro.data.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    fun parseMinorUnits(value: String, allowNegative: Boolean = false): Long {
        val normalized = value.trim().replace(',', '.')
        require(normalized.isNotEmpty()) { "Amount is required" }
        require(normalized.count { it == '.' } <= 1) { "Enter a valid amount" }

        val decimal = normalized.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Enter a valid amount")
        if (!allowNegative) {
            require(decimal > BigDecimal.ZERO) { "Amount must be greater than zero" }
        }

        return decimal
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    }

    fun format(
        amountMinor: Long,
        currencyCode: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(currencyCode)
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return formatter.format(BigDecimal.valueOf(amountMinor, 2))
    }
}
