package it.rfmariano.denaro.data.migration

import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.RecurringRuleEntity
import it.rfmariano.denaro.data.local.TransactionEntity
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.data.local.TransferEntity
import it.rfmariano.denaro.data.local.UuidV7
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

data class TransformedLegacyData(
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val recurringRules: List<RecurringRuleEntity>,
    val transfers: List<TransferEntity>,
    val expectedBalances: Map<String, Long>,
    val warnings: List<String>,
)

class LegacyTransformer(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val idGenerator: (Long) -> String = UuidV7::generate,
) {
    fun transform(snapshot: LegacySnapshot): TransformedLegacyData {
        validate(snapshot)
        val bucketsById = snapshot.buckets.associateBy(LegacyBucket::id)
        val transferPairs = findTransferPairs(snapshot.transactions, bucketsById)
        val transferredIds = transferPairs
            .flatMapTo(mutableSetOf()) { listOf(it.outgoing.id, it.incoming.id) }
        val warnings = mutableListOf<String>()
        val rulesByTransactionId = mutableMapOf<String, RecurringRuleEntity>()

        snapshot.transactions
            .asSequence()
            .filterNot { it.id in transferredIds }
            .filter { it.intervalValue != null || it.intervalUnit != null }
            .forEach { transaction ->
                val rule = createRecurringRule(transaction, warnings)
                if (rule != null) rulesByTransactionId[transaction.id] = rule
            }

        val accounts = snapshot.buckets.map { bucket ->
            AccountEntity(
                id = bucket.id,
                name = bucket.title.trim(),
                description = bucket.description?.trim()?.takeIf(String::isNotEmpty),
                openingBalanceMinor = bucket.initialBalanceMinor,
                currency = bucket.currency,
                archivedAt = null,
                createdAt = bucket.createdAt,
                updatedAt = bucket.createdAt,
            )
        }
        val transactions = snapshot.transactions
            .filterNot { it.id in transferredIds }
            .map { legacy ->
                val rule = rulesByTransactionId[legacy.id]
                TransactionEntity(
                    id = legacy.id,
                    accountId = legacy.bucketId,
                    recurringRuleId = rule?.id,
                    occurrenceKey = rule?.let { "legacy:${legacy.id}" },
                    amountMinor = abs(legacy.amountMinor),
                    type = legacy.amountMinor.toTransactionType(),
                    occurredAt = legacy.date,
                    localDate = legacy.date.toLocalDate(),
                    description = legacy.description?.trim()?.takeIf(String::isNotEmpty),
                    createdAt = legacy.date,
                    updatedAt = legacy.date,
                )
            }
        val transfers = transferPairs.map { pair ->
            TransferEntity(
                id = idGenerator(pair.outgoing.date),
                fromAccountId = pair.outgoing.bucketId,
                toAccountId = pair.incoming.bucketId,
                amountMinor = abs(pair.outgoing.amountMinor),
                occurredAt = pair.outgoing.date,
                localDate = pair.outgoing.date.toLocalDate(),
                description = pair.outgoing.description?.trim()?.takeIf(String::isNotEmpty),
                createdAt = pair.outgoing.date,
                updatedAt = pair.outgoing.date,
            )
        }

        return TransformedLegacyData(
            accounts = accounts,
            transactions = transactions,
            recurringRules = rulesByTransactionId.values.toList(),
            transfers = transfers,
            expectedBalances = expectedBalances(snapshot),
            warnings = warnings,
        )
    }

    private fun validate(snapshot: LegacySnapshot) {
        val duplicateBucket = snapshot.buckets.groupingBy(LegacyBucket::id)
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
        require(duplicateBucket == null) {
            "Duplicate legacy bucket ID ${duplicateBucket?.key}"
        }
        val bucketIds = snapshot.buckets.mapTo(mutableSetOf(), LegacyBucket::id)
        snapshot.buckets.forEach {
            require(it.id.isNotBlank()) { "Legacy bucket ID is blank" }
            require(it.title.isNotBlank()) { "Legacy bucket ${it.id} has no title" }
            require(CURRENCY.matches(it.currency)) {
                "Legacy bucket ${it.id} has invalid currency ${it.currency}"
            }
        }

        val duplicateTransaction = snapshot.transactions.groupingBy(LegacyTransaction::id)
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
        require(duplicateTransaction == null) {
            "Duplicate legacy transaction ID ${duplicateTransaction?.key}"
        }
        snapshot.transactions.forEach {
            require(it.id.isNotBlank()) { "Legacy transaction ID is blank" }
            require(it.bucketId in bucketIds) {
                "Legacy transaction ${it.id} references a missing bucket"
            }
            require(it.amountMinor != 0L && it.amountMinor != Long.MIN_VALUE) {
                "Legacy transaction ${it.id} has an invalid amount"
            }
            require(it.date >= 0) { "Legacy transaction ${it.id} has an invalid date" }
            require((it.intervalValue == null) == (it.intervalUnit == null)) {
                "Legacy transaction ${it.id} has an incomplete recurrence"
            }
            require(it.intervalValue == null || it.intervalValue > 0) {
                "Legacy transaction ${it.id} has an invalid recurrence interval"
            }
        }
    }

    private fun findTransferPairs(
        transactions: List<LegacyTransaction>,
        bucketsById: Map<String, LegacyBucket>,
    ): List<TransferPair> {
        val outgoing = transactions.filter {
            it.amountMinor < 0 && it.intervalValue == null
        }
        val incoming = transactions.filter {
            it.amountMinor > 0 && it.intervalValue == null
        }
        val candidates = outgoing.associateWith { source ->
            incoming.filter { target ->
                source.bucketId != target.bucketId &&
                        source.amountMinor != Long.MIN_VALUE &&
                        -source.amountMinor == target.amountMinor &&
                        abs(source.date - target.date) <= TRANSFER_WINDOW_MILLIS &&
                        bucketsById.getValue(source.bucketId).currency ==
                        bucketsById.getValue(target.bucketId).currency &&
                        normalizeDescription(source.description) ==
                        normalizeDescription(target.description)
            }
        }

        return buildList {
            candidates.forEach { (source, targets) ->
                if (targets.size != 1) return@forEach
                val target = targets.single()
                val reverseMatches = candidates.count { (_, values) -> target in values }
                if (reverseMatches == 1) add(TransferPair(source, target))
            }
        }
    }

    private fun createRecurringRule(
        transaction: LegacyTransaction,
        warnings: MutableList<String>,
    ): RecurringRuleEntity? {
        val frequency = when (transaction.intervalUnit) {
            "days" -> RecurrenceFrequency.DAILY
            "weeks" -> RecurrenceFrequency.WEEKLY
            "months" -> RecurrenceFrequency.MONTHLY
            "years" -> RecurrenceFrequency.YEARLY
            "seconds" -> {
                warnings += "Second-based recurrence ${transaction.id} was disabled"
                return null
            }

            else -> throw IllegalArgumentException(
                "Unsupported recurrence unit ${transaction.intervalUnit}",
            )
        }
        val intervalCount = requireNotNull(transaction.intervalValue)
        val anchor = Instant.ofEpochMilli(transaction.date).atZone(zoneId)
        val anchorDay = when (frequency) {
            RecurrenceFrequency.MONTHLY, RecurrenceFrequency.YEARLY ->
                transaction.dayOfMonth ?: anchor.dayOfMonth

            else -> null
        }
        val anchorMonth = when (frequency) {
            RecurrenceFrequency.YEARLY -> transaction.month ?: anchor.monthValue
            else -> null
        }
        val next = calculateNext(anchor, frequency, intervalCount, anchorDay, anchorMonth)

        return RecurringRuleEntity(
            id = idGenerator(transaction.date),
            accountId = transaction.bucketId,
            amountMinor = abs(transaction.amountMinor),
            transactionType = transaction.amountMinor.toTransactionType(),
            description = transaction.description?.trim()?.takeIf(String::isNotEmpty),
            frequency = frequency,
            intervalCount = intervalCount,
            timezoneId = zoneId.id,
            anchorDay = anchorDay,
            anchorMonth = anchorMonth,
            startAt = transaction.date,
            lastGeneratedAt = transaction.date,
            nextOccurrenceAt = next.toInstant().toEpochMilli(),
            isActive = true,
            createdAt = transaction.date,
            updatedAt = transaction.date,
        )
    }

    private fun calculateNext(
        anchor: ZonedDateTime,
        frequency: RecurrenceFrequency,
        interval: Int,
        anchorDay: Int?,
        anchorMonth: Int?,
    ): ZonedDateTime = when (frequency) {
        RecurrenceFrequency.DAILY -> anchor.plusDays(interval.toLong())
        RecurrenceFrequency.WEEKLY -> anchor.plusWeeks(interval.toLong())
        RecurrenceFrequency.MONTHLY -> {
            val targetMonth = YearMonth.from(anchor).plusMonths(interval.toLong())
            anchor
                .plusMonths(interval.toLong())
                .withDayOfMonth(minOf(requireNotNull(anchorDay), targetMonth.lengthOfMonth()))
        }

        RecurrenceFrequency.YEARLY -> {
            val targetYear = anchor.year + interval
            val targetMonth = requireNotNull(anchorMonth)
            val targetDay = minOf(
                requireNotNull(anchorDay),
                YearMonth.of(targetYear, targetMonth).lengthOfMonth(),
            )
            ZonedDateTime.of(
                LocalDate.of(targetYear, targetMonth, targetDay),
                anchor.toLocalTime(),
                anchor.zone,
            )
        }
    }

    private fun expectedBalances(snapshot: LegacySnapshot): Map<String, Long> {
        val balances = snapshot.buckets.associate {
            it.id to it.initialBalanceMinor
        }.toMutableMap()
        snapshot.transactions.forEach {
            balances[it.bucketId] = Math.addExact(
                balances.getValue(it.bucketId),
                it.amountMinor,
            )
        }
        return balances
    }

    private fun Long.toTransactionType(): TransactionType =
        if (this > 0) TransactionType.INCOME else TransactionType.EXPENSE

    private fun Long.toLocalDate(): String =
        Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate().toString()

    private fun normalizeDescription(value: String?): String =
        value?.trim()?.lowercase().orEmpty()

    private data class TransferPair(
        val outgoing: LegacyTransaction,
        val incoming: LegacyTransaction,
    )

    private companion object {
        const val TRANSFER_WINDOW_MILLIS = 1_000L
        val CURRENCY = Regex("[A-Z]{3}")
    }
}
