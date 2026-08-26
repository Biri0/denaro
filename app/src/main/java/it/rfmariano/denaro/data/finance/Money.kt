package it.rfmariano.denaro.data.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    const val MIN_FRACTION_DIGITS = 0
    const val MAX_FRACTION_DIGITS = 9

    fun parseMinorUnits(
        value: String,
        fractionDigits: Int,
        allowNegative: Boolean = false,
    ): Long {
        requireValidFractionDigits(fractionDigits)
        val normalized = value.trim().replace(',', '.')
        require(normalized.isNotEmpty()) { "Amount is required" }
        require(normalized.count { it == '.' } <= 1) { "Enter a valid amount" }

        val decimal = normalized.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Enter a valid amount")
        if (!allowNegative) {
            require(decimal > BigDecimal.ZERO) { "Amount must be greater than zero" }
        }

        val scaled = try {
            decimal.setScale(fractionDigits, RoundingMode.UNNECESSARY)
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Enter a valid amount")
        }
        return scaled
            .movePointRight(fractionDigits)
            .longValueExact()
    }

    fun format(
        amountMinor: Long,
        currencyCode: String,
        fractionDigits: Int,
        locale: Locale = Locale.getDefault(),
    ): String {
        requireValidFractionDigits(fractionDigits)
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(currencyCode)
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
        return formatter.format(BigDecimal.valueOf(amountMinor, fractionDigits))
    }

    fun toInputAmount(amountMinor: Long, fractionDigits: Int): String {
        requireValidFractionDigits(fractionDigits)
        return BigDecimal.valueOf(amountMinor, fractionDigits)
            .stripTrailingZeros()
            .toPlainString()
    }

    fun rescaleInput(text: String, fromFractionDigits: Int?, toFractionDigits: Int?): String {
        if (text.isBlank() || fromFractionDigits == null || toFractionDigits == null ||
            fromFractionDigits == toFractionDigits
        ) {
            return text
        }
        val minor = runCatching {
            parseMinorUnits(text, fromFractionDigits, allowNegative = true)
        }.getOrNull() ?: return text
        return toInputAmount(convertMinorUnits(minor, fromFractionDigits, toFractionDigits), toFractionDigits)
    }

    fun isValidFractionDigits(fractionDigits: Int): Boolean =
        fractionDigits in MIN_FRACTION_DIGITS..MAX_FRACTION_DIGITS

    /** Returns current platform metadata, or null for unknown/pseudo-currencies. */
    fun knownFractionDigits(currencyCode: String): Int? =
        runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
            .getOrNull()
            ?.takeIf { it >= 0 && isValidFractionDigits(it) }

    /** Resolves a currency's fraction digits, falling back to 2 when unknown/undefined. */
    fun fractionDigitsForCurrency(currencyCode: String): Int =
        knownFractionDigits(currencyCode) ?: 2

    /** Converts an amount in minor units between fraction-digit scales, truncating toward zero. */
    fun convertMinorUnits(amountMinor: Long, fromFractionDigits: Int, toFractionDigits: Int): Long {
        requireValidFractionDigits(fromFractionDigits)
        requireValidFractionDigits(toFractionDigits)
        if (fromFractionDigits == toFractionDigits) return amountMinor
        val exponent = toFractionDigits - fromFractionDigits
        val factor = pow10(kotlin.math.abs(exponent))
        return if (exponent > 0) amountMinor * factor else amountMinor / factor
    }

    private fun pow10(exponent: Int): Long {
        var value = 1L
        repeat(exponent) { value *= 10L }
        return value
    }

    private fun requireValidFractionDigits(fractionDigits: Int) {
        require(isValidFractionDigits(fractionDigits)) {
            "Fraction digits must be between $MIN_FRACTION_DIGITS and $MAX_FRACTION_DIGITS"
        }
    }
}
