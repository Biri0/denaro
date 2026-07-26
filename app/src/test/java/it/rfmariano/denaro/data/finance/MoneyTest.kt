package it.rfmariano.denaro.data.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

class MoneyTest {
    @Test
    fun parsesPeriodAndCommaUsingFixedTwoDecimalStorage() {
        assertEquals(1_234L, Money.parseMinorUnits("12.34"))
        assertEquals(1_234L, Money.parseMinorUnits("12,34"))
        assertEquals(-50L, Money.parseMinorUnits("-0.50", allowNegative = true))
    }

    @Test
    fun rejectsExcessPrecisionAndNonPositiveMovementAmounts() {
        assertThrows(ArithmeticException::class.java) {
            Money.parseMinorUnits("1.001")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Money.parseMinorUnits("0")
        }
    }

    @Test
    fun keepsTwoDecimalsForJpyCompatibility() {
        assertEquals("¥12.34", Money.format(1_234, "JPY", Locale.US))
    }
}
