package it.rfmariano.denaro.data.export

import it.rfmariano.denaro.data.local.BalanceAdjustmentEntity
import it.rfmariano.denaro.data.local.DebtEntity
import it.rfmariano.denaro.data.local.DebtRepaymentEntity
import it.rfmariano.denaro.data.local.TransactionEntity
import it.rfmariano.denaro.data.local.TransferEntity

enum class StatementColumn {
    ACCOUNT,
    DATE,
    DESCRIPTION,
    CATEGORY,
    CREDIT,
    DEBIT,
    BALANCE,
}

val DEFAULT_STATEMENT_COLUMNS: Set<StatementColumn> =
    StatementColumn.entries.filter { it != StatementColumn.ACCOUNT }.toSet()

enum class StatementDateRange {
    THIS_MONTH,
    LAST_MONTH,
    THIS_YEAR,
    LAST_YEAR,
    ALL_TIME,
    CUSTOM,
}

enum class StatementLayout {
    GROUPED,
    CHRONOLOGICAL,
}

data class StatementAccount(
    val id: String,
    val name: String,
    val currency: String,
    val fractionDigits: Int,
    val openingBalanceMinor: Long,
)

data class StatementSnapshot(
    val accounts: List<StatementAccount>,
    val accountNames: Map<String, String>,
    val categoryNames: Map<String, String>,
    val counterpartyNames: Map<String, String>,
    val transactions: List<TransactionEntity>,
    val transfers: List<TransferEntity>,
    val balanceAdjustments: List<BalanceAdjustmentEntity>,
    val debts: List<DebtEntity>,
    val debtRepayments: List<DebtRepaymentEntity>,
)

data class StatementRow(
    val accountId: String,
    val accountName: String,
    val fractionDigits: Int,
    val occurredAt: Long,
    val localDate: String,
    val description: String,
    val categoryName: String?,
    val creditMinor: Long?,
    val debitMinor: Long?,
    val deltaMinor: Long,
)

data class BalancedStatementRow(
    val row: StatementRow,
    val balanceMinor: Long,
)

data class StatementExport(
    val content: String,
    val rowCount: Int,
)
