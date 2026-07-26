package it.rfmariano.denaro.data.finance

import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

object RecurrenceCalculator {
    fun nextOccurrence(rule: RecurringRuleEntity, occurrenceAt: Long): Long {
        val occurrence = Instant.ofEpochMilli(occurrenceAt).atZone(
            ZoneId.of(rule.timezoneId),
        )
        return when (rule.frequency) {
            RecurrenceFrequency.DAILY -> occurrence.plusDays(rule.intervalCount.toLong())
            RecurrenceFrequency.WEEKLY -> occurrence.plusWeeks(rule.intervalCount.toLong())
            RecurrenceFrequency.MONTHLY -> occurrence.nextMonth(rule)
            RecurrenceFrequency.YEARLY -> occurrence.nextYear(rule)
        }.toInstant().toEpochMilli()
    }

    private fun ZonedDateTime.nextMonth(rule: RecurringRuleEntity): ZonedDateTime {
        val targetMonth = YearMonth.from(this).plusMonths(rule.intervalCount.toLong())
        return plusMonths(rule.intervalCount.toLong()).withDayOfMonth(
            minOf(requireNotNull(rule.anchorDay), targetMonth.lengthOfMonth()),
        )
    }

    private fun ZonedDateTime.nextYear(rule: RecurringRuleEntity): ZonedDateTime {
        val targetYear = year + rule.intervalCount
        val targetMonth = requireNotNull(rule.anchorMonth)
        val targetDay = minOf(
            requireNotNull(rule.anchorDay),
            YearMonth.of(targetYear, targetMonth).lengthOfMonth(),
        )
        return ZonedDateTime.of(
            LocalDate.of(targetYear, targetMonth, targetDay),
            toLocalTime(),
            zone,
        )
    }
}
