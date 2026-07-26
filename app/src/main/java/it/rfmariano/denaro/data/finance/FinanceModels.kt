package it.rfmariano.denaro.data.finance

import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.TransactionType

enum class ActivityKind {
    INCOME,
    EXPENSE,
    TRANSFER,
}

data class AccountSummary(
    val id: String,
    val name: String,
    val description: String?,
    val openingBalanceMinor: Long,
    val balanceMinor: Long,
    val currency: String,
    val archivedAt: Long?,
)

data class ActivityItem(
    val id: String,
    val kind: ActivityKind,
    val accountId: String,
    val counterpartyAccountId: String?,
    val accountName: String,
    val counterpartyAccountName: String?,
    val currency: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val localDate: String,
    val description: String?,
    val recurringRuleId: String?,
)

data class RecurringRuleSummary(
    val id: String,
    val amountMinor: Long,
    val transactionType: TransactionType,
    val description: String?,
    val frequency: RecurrenceFrequency,
    val intervalCount: Int,
    val nextOccurrenceAt: Long,
    val isActive: Boolean,
)

data class AccountInput(
    val name: String,
    val description: String?,
    val openingBalanceMinor: Long,
    val currency: String,
)

data class TransactionInput(
    val accountId: String,
    val amountMinor: Long,
    val type: TransactionType,
    val occurredAt: Long,
    val description: String?,
)

data class TransferInput(
    val fromAccountId: String,
    val toAccountId: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val description: String?,
)

data class TransferAccountSuggestions(
    val preferredSourceId: String? = null,
    val preferredDestinationIds: Map<String, String> = emptyMap(),
)

val SupportedCurrencies = listOf("EUR", "USD", "GBP", "JPY", "CHF", "CAD", "AUD", "CNY")
