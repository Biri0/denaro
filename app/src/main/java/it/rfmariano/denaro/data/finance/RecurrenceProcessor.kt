package it.rfmariano.denaro.data.finance

import androidx.room.withTransaction
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import it.rfmariano.denaro.data.local.TransactionEntity
import it.rfmariano.denaro.data.local.UuidV7
import java.time.Instant

class RecurrenceProcessor(
    private val database: DenaroDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun processDueRules() {
        val processingTime = now()
        database.withTransaction {
            database.recurringRuleDao().getDue(processingTime).forEach { rule ->
                processRule(rule, processingTime)
            }
        }
    }

    private suspend fun processRule(initialRule: RecurringRuleEntity, processingTime: Long) {
        var rule = initialRule
        while (rule.nextOccurrenceAt <= processingTime) {
            val occurrenceAt = rule.nextOccurrenceAt
            val occurrenceKey = occurrenceAt.toString()
            database.transactionDao().insertIfAbsent(
                TransactionEntity(
                    id = UuidV7.generate(),
                    accountId = rule.accountId,
                    recurringRuleId = rule.id,
                    occurrenceKey = occurrenceKey,
                    amountMinor = rule.amountMinor,
                    type = rule.transactionType,
                    occurredAt = occurrenceAt,
                    localDate = occurrenceAt.toLocalDate(rule.timezoneId),
                    description = rule.description,
                    createdAt = processingTime,
                    updatedAt = processingTime,
                ),
            )
            rule = rule.copy(
                lastGeneratedAt = occurrenceAt,
                nextOccurrenceAt = RecurrenceCalculator.nextOccurrence(rule, occurrenceAt),
                updatedAt = processingTime,
            )
            database.recurringRuleDao().update(rule)
        }
    }

    private fun Long.toLocalDate(timezoneId: String): String =
        Instant.ofEpochMilli(this)
            .atZone(java.time.ZoneId.of(timezoneId))
            .toLocalDate()
            .toString()
}
