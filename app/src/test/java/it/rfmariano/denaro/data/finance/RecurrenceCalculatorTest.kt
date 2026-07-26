package it.rfmariano.denaro.data.finance

import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import it.rfmariano.denaro.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceCalculatorTest {
    @Test
    fun monthlyRuleClampsAndReturnsToAnchorDay() {
        val zone = ZoneId.of("Europe/Rome")
        val january = ZonedDateTime.of(2024, 1, 31, 9, 30, 0, 0, zone)
        val rule = rule(
            frequency = RecurrenceFrequency.MONTHLY,
            timezoneId = zone.id,
            anchorDay = 31,
        )

        val february = RecurrenceCalculator.nextOccurrence(
            rule,
            january.toInstant().toEpochMilli(),
        )
        val march = RecurrenceCalculator.nextOccurrence(rule, february)

        assertEquals(29, february.atZone(zone).dayOfMonth)
        assertEquals(31, march.atZone(zone).dayOfMonth)
    }

    @Test
    fun dailyRulePreservesLocalTimeAcrossDst() {
        val zone = ZoneId.of("Europe/Rome")
        val beforeDst = ZonedDateTime.of(2026, 3, 28, 9, 0, 0, 0, zone)
        val next = RecurrenceCalculator.nextOccurrence(
            rule(
                frequency = RecurrenceFrequency.DAILY,
                timezoneId = zone.id,
            ),
            beforeDst.toInstant().toEpochMilli(),
        ).atZone(zone)

        assertEquals(9, next.hour)
        assertEquals(29, next.dayOfMonth)
    }

    private fun rule(
        frequency: RecurrenceFrequency,
        timezoneId: String,
        anchorDay: Int? = null,
    ) = RecurringRuleEntity(
        id = "rule",
        accountId = "account",
        amountMinor = 100,
        transactionType = TransactionType.EXPENSE,
        description = null,
        frequency = frequency,
        intervalCount = 1,
        timezoneId = timezoneId,
        anchorDay = anchorDay,
        anchorMonth = null,
        startAt = 0,
        lastGeneratedAt = null,
        nextOccurrenceAt = 0,
        isActive = true,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun Long.atZone(zoneId: ZoneId): ZonedDateTime =
        java.time.Instant.ofEpochMilli(this).atZone(zoneId)
}
