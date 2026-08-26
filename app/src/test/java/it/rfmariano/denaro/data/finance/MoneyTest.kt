package it.rfmariano.denaro.data.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyTest {
    @Test
    fun parsesPeriodAndCommaUsingAuthoritativePrecision() {
        assertEquals(1_234L, Money.parseMinorUnits("12.34", 2))
        assertEquals(1_234L, Money.parseMinorUnits("12,34", 2))
        assertEquals(-50L, Money.parseMinorUnits("-0.50", 2, allowNegative = true))
    }

    @Test
    fun rejectsExcessPrecisionAndNonPositiveMovementAmounts() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.parseMinorUnits("1.001", 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Money.parseMinorUnits("0", 2)
        }
    }

    @Test
    fun usesPersistedFractionDigitsIndependentlyFromCurrencyMetadata() {
        assertEquals(1_234L, Money.parseMinorUnits("1234", 0))
        assertEquals(1_234L, Money.parseMinorUnits("1.234", 3))
        assertEquals("¥1,234", Money.format(1_234, "JPY", 0, Locale.US))
        assertEquals("12.34", Money.toInputAmount(1_234, 2))
        assertTrue(Money.format(1_234, "JPY", 2, Locale.US).contains("12.34"))
    }

    @Test
    fun getsFractionDigitsFromCurrencyLibrary() {
        assertEquals(2, Money.fractionDigitsForCurrency("EUR"))
        assertEquals(0, Money.fractionDigitsForCurrency("JPY"))
        assertEquals(3, Money.fractionDigitsForCurrency("KWD"))
        assertNull(Money.knownFractionDigits("XYZ"))
        assertEquals(2, Money.fractionDigitsForCurrency("XYZ"))
    }

    @Test
    fun convertsMinorUnitsBetweenScalesTruncatingTowardZero() {
        assertEquals(12L, Money.convertMinorUnits(1_234, 2, 0))
        assertEquals(123_400L, Money.convertMinorUnits(1_234, 2, 4))
        assertEquals(1_234L, Money.convertMinorUnits(1_234, 2, 2))
        assertEquals(1_200L, Money.convertMinorUnits(12, 0, 2))
        assertEquals(-12L, Money.convertMinorUnits(-1_234, 2, 0))
        assertEquals(1_234L, Money.convertMinorUnits(123_400L, 4, 2))
    }

    @Test
    fun rescaleInputPreservesValueAcrossScales() {
        assertEquals("12", Money.rescaleInput("12.34", 2, 0))
        assertEquals("500", Money.rescaleInput("500", 0, 2))
        assertEquals("5", Money.rescaleInput("5", 0, 2))
        assertEquals("12.34", Money.rescaleInput("12.34", 2, 4))
    }

    @Test
    fun rescaleInputPassesThroughWhenNoConversionIsNeeded() {
        assertEquals("12.34", Money.rescaleInput("12.34", 2, 2))
        assertEquals("12.34", Money.rescaleInput("12.34", null, 0))
        assertEquals("12.34", Money.rescaleInput("12.34", 2, null))
        assertEquals("", Money.rescaleInput("", 2, 0))
        assertEquals("   ", Money.rescaleInput("   ", 2, 0))
    }

    @Test
    fun rejectsPrecisionBeyondCurrencyFractionDigits() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.parseMinorUnits("1.001", 0)
        }
    }

    @Test
    fun rejectsFractionDigitsOutsidePersistedBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.parseMinorUnits("1", Money.MIN_FRACTION_DIGITS - 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Money.toInputAmount(1, Money.MAX_FRACTION_DIGITS + 1)
        }
    }
}
