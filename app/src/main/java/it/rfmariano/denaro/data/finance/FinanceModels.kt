package it.rfmariano.denaro.data.finance

import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.TransactionType

enum class ActivityKind {
    INCOME,
    EXPENSE,
    TRANSFER,
    DEBT,
}

enum class DebtMovementKind { OPENING, REPAYMENT }

const val UNCATEGORIZED_CATEGORY_FILTER = "__uncategorized__"

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
    val categoryId: String? = null,
    val categoryParentId: String? = null,
    val categoryName: String? = null,
    val categoryIconName: String? = null,
    val categoryColorIndex: Int? = null,
    val debtId: String? = null,
    val debtDirection: DebtDirection? = null,
    val debtMovement: DebtMovementKind? = null,
    val externalCounterpartyName: String? = null,
)

data class CounterpartySummary(
    val id: String,
    val name: String,
    val note: String?,
    val archivedAt: Long?,
)

data class CounterpartyInput(val name: String, val note: String?)

data class DebtSummary(
    val id: String,
    val counterpartyId: String,
    val counterpartyName: String,
    val accountId: String,
    val accountName: String,
    val direction: DebtDirection,
    val principalMinor: Long,
    val repaidMinor: Long,
    val currency: String,
    val openedAt: Long,
    val localDate: String,
    val dueDate: String?,
    val note: String?,
) {
    val outstandingMinor: Long get() = principalMinor - repaidMinor
    val isSettled: Boolean get() = outstandingMinor == 0L
}

data class DebtInput(
    val counterpartyId: String,
    val accountId: String,
    val direction: DebtDirection,
    val principalMinor: Long,
    val openedAt: Long,
    val dueDate: String?,
    val note: String?,
)

data class DebtRepaymentSummary(
    val id: String,
    val debtId: String,
    val accountId: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val note: String?,
)

data class DebtRepaymentInput(
    val debtId: String,
    val accountId: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val note: String?,
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
    val categoryId: String? = null,
)

data class CategorySummary(
    val id: String,
    val type: TransactionType,
    val parentId: String?,
    val name: String,
    val iconName: String,
    val colorIndex: Int,
    val archivedAt: Long?,
)

data class CategoryInput(
    val type: TransactionType,
    val parentId: String?,
    val name: String,
    val iconName: String,
    val colorIndex: Int,
)

data class ActivityFilter(
    val kind: ActivityKind? = null,
    val accountId: String? = null,
    val currency: String? = null,
    val categoryId: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
)

data class DashboardFilter(
    val currency: String,
    val accountId: String?,
    val selectedMonth: String,
)

data class MonthlyCashFlow(
    val month: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}

data class CategoryShare(
    val categoryId: String?,
    val name: String?,
    val iconName: String?,
    val colorIndex: Int?,
    val amountMinor: Long,
    val transactionCount: Int,
)

data class DashboardSnapshot(
    val filter: DashboardFilter,
    val months: List<MonthlyCashFlow>,
    val selected: MonthlyCashFlow,
    val previousComparable: MonthlyCashFlow,
    val incomeCategories: List<CategoryShare>,
    val expenseCategories: List<CategoryShare>,
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
    val categoryId: String? = null,
)

data class TransferInput(
    val fromAccountId: String,
    val toAccountId: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val description: String?,
)

data class RecurringRuleInput(
    val accountId: String,
    val amountMinor: Long,
    val transactionType: TransactionType,
    val description: String?,
    val frequency: RecurrenceFrequency,
    val intervalCount: Int,
    val nextOccurrenceAt: Long,
    val categoryId: String? = null,
)

data class TransferAccountSuggestions(
    val preferredSourceId: String? = null,
    val preferredDestinationIds: Map<String, String> = emptyMap(),
)

val SupportedCurrencies = listOf("EUR", "USD", "GBP", "JPY", "CHF", "CAD", "AUD", "CNY")
