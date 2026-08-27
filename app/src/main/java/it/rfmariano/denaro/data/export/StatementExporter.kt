package it.rfmariano.denaro.data.export

import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.data.local.DebtEntity
import it.rfmariano.denaro.data.local.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

fun statementDateRangeBounds(
    range: StatementDateRange,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    today: LocalDate = LocalDate.now(),
): Pair<LocalDate?, LocalDate?> = when (range) {
    StatementDateRange.THIS_MONTH -> YearMonth.from(today).atDay(1) to today
    StatementDateRange.LAST_MONTH -> {
        val previous = YearMonth.from(today).minusMonths(1)
        previous.atDay(1) to previous.atEndOfMonth()
    }

    StatementDateRange.THIS_YEAR -> LocalDate.of(today.year, 1, 1) to today
    StatementDateRange.LAST_YEAR ->
        LocalDate.of(today.year - 1, 1, 1) to LocalDate.of(today.year - 1, 12, 31)

    StatementDateRange.ALL_TIME -> null to null
    StatementDateRange.CUSTOM -> customFrom to customTo
}

data class StatementLabels(
    val columnAccount: String = "Account",
    val columnDate: String = "Date",
    val columnDescription: String = "Description",
    val columnCategory: String = "Category",
    val columnCredit: String = "Credit",
    val columnDebit: String = "Debit",
    val columnBalance: String = "Balance",
    val transferFrom: (String) -> String = { "Transfer from $it" },
    val transferTo: (String) -> String = { "Transfer to $it" },
    val balanceAdjustment: String = "Balance adjustment",
    val borrowedFrom: (String) -> String = { "Borrowed from $it" },
    val lentTo: (String) -> String = { "Lent to $it" },
    val repaidTo: (String) -> String = { "Repaid to $it" },
    val repaidBy: (String) -> String = { "Repaid by $it" },
)

class StatementExporter(
    private val labels: StatementLabels = StatementLabels(),
) {
    fun rows(snapshot: StatementSnapshot): List<StatementRow> {
        val debtsById = snapshot.debts.associateBy { it.id }
        val counterpartyName: (String) -> String = { id ->
            snapshot.counterpartyNames[id].orEmpty()
        }

        val rows = ArrayList<StatementRow>()
        snapshot.accounts.forEach { account ->
            rows += transactionRows(snapshot, account)
            rows += transferRows(snapshot, account)
            rows += adjustmentRows(snapshot, account)
            rows += debtRows(snapshot, account, counterpartyName)
            rows += repaymentRows(snapshot, account, debtsById, counterpartyName)
        }
        return rows.sortedWith(
            compareBy<StatementRow> { it.accountName.lowercase() }
                .thenBy { LocalDate.parse(it.localDate) }
                .thenBy { it.occurredAt }
                .thenBy { it.description },
        )
    }

    fun export(
        snapshot: StatementSnapshot,
        columns: Set<StatementColumn>,
        fromDate: LocalDate?,
        toDate: LocalDate?,
        layout: StatementLayout,
    ): StatementExport {
        fun inRange(row: StatementRow): Boolean {
            val date = LocalDate.parse(row.localDate)
            return (fromDate == null || date >= fromDate) && (toDate == null || date <= toDate)
        }

        val balances = snapshot.accounts.associate { it.id to it.openingBalanceMinor }
            .toMutableMap()
        val allBalanced = ArrayList<BalancedStatementRow>()
        for (row in rows(snapshot)) {
            val next = (balances[row.accountId] ?: 0L) + row.deltaMinor
            balances[row.accountId] = next
            allBalanced += BalancedStatementRow(row, next)
        }

        val hasCredit = StatementColumn.CREDIT in columns
        val hasDebit = StatementColumn.DEBIT in columns
        val monetaryFilterApplies = hasCredit != hasDebit
        val filtered = allBalanced.filter { balanced ->
            inRange(balanced.row) && (
                    !monetaryFilterApplies ||
                            (hasCredit && balanced.row.creditMinor != null) ||
                            (hasDebit && balanced.row.debitMinor != null)
                    )
        }

        val ordered = when (layout) {
            StatementLayout.GROUPED -> filtered
            StatementLayout.CHRONOLOGICAL -> filtered.sortedWith(
                compareBy<BalancedStatementRow> { it.row.occurredAt }
                    .thenBy { it.row.accountName.lowercase() }
                    .thenBy { it.row.description },
            )
        }

        val multiAccount = snapshot.accounts.size > 1
        val effectiveColumns = if (multiAccount) columns else columns - StatementColumn.ACCOUNT
        val body = csv(ordered, effectiveColumns)
        return StatementExport(content = BOM + body, rowCount = ordered.size)
    }

    private fun transactionRows(
        snapshot: StatementSnapshot,
        account: StatementAccount,
    ): List<StatementRow> = snapshot.transactions
        .filter { it.accountId == account.id }
        .map { transaction ->
            val isIncome = transaction.type == TransactionType.INCOME
            StatementRow(
                accountId = account.id,
                accountName = account.name,
                fractionDigits = account.fractionDigits,
                occurredAt = transaction.occurredAt,
                localDate = transaction.localDate,
                description = transaction.description.orEmpty(),
                categoryName = transaction.categoryId?.let(snapshot.categoryNames::get),
                creditMinor = if (isIncome) transaction.amountMinor else null,
                debitMinor = if (isIncome) null else transaction.amountMinor,
                deltaMinor = if (isIncome) transaction.amountMinor else -transaction.amountMinor,
            )
        }

    private fun transferRows(
        snapshot: StatementSnapshot,
        account: StatementAccount,
    ): List<StatementRow> = snapshot.transfers
        .filter { it.fromAccountId == account.id || it.toAccountId == account.id }
        .map { transfer ->
            val outgoing = transfer.fromAccountId == account.id
            val counterparty = if (outgoing) {
                snapshot.accountNames[transfer.toAccountId].orEmpty()
            } else {
                snapshot.accountNames[transfer.fromAccountId].orEmpty()
            }
            StatementRow(
                accountId = account.id,
                accountName = account.name,
                fractionDigits = account.fractionDigits,
                occurredAt = transfer.occurredAt,
                localDate = transfer.localDate,
                description = transfer.description?.takeIf(String::isNotBlank)
                    ?: if (outgoing) labels.transferTo(counterparty) else labels.transferFrom(
                        counterparty
                    ),
                categoryName = null,
                creditMinor = if (outgoing) null else transfer.amountMinor,
                debitMinor = if (outgoing) transfer.amountMinor else null,
                deltaMinor = if (outgoing) -transfer.amountMinor else transfer.amountMinor,
            )
        }

    private fun adjustmentRows(
        snapshot: StatementSnapshot,
        account: StatementAccount,
    ): List<StatementRow> = snapshot.balanceAdjustments
        .filter { it.accountId == account.id }
        .map { adjustment ->
            val positive = adjustment.deltaMinor >= 0
            StatementRow(
                accountId = account.id,
                accountName = account.name,
                fractionDigits = account.fractionDigits,
                occurredAt = adjustment.occurredAt,
                localDate = adjustment.localDate,
                description = labels.balanceAdjustment,
                categoryName = null,
                creditMinor = if (positive) adjustment.deltaMinor else null,
                debitMinor = if (positive) null else -adjustment.deltaMinor,
                deltaMinor = adjustment.deltaMinor,
            )
        }

    private fun debtRows(
        snapshot: StatementSnapshot,
        account: StatementAccount,
        counterpartyName: (String) -> String,
    ): List<StatementRow> = snapshot.debts
        .filter { it.accountId == account.id }
        .map { debt ->
            val name = counterpartyName(debt.counterpartyId)
            val borrowed = debt.direction == DebtDirection.BORROWED
            StatementRow(
                accountId = account.id,
                accountName = account.name,
                fractionDigits = account.fractionDigits,
                occurredAt = debt.openedAt,
                localDate = debt.localDate,
                description = if (borrowed) labels.borrowedFrom(name) else labels.lentTo(name),
                categoryName = null,
                creditMinor = if (borrowed) debt.principalMinor else null,
                debitMinor = if (borrowed) null else debt.principalMinor,
                deltaMinor = if (borrowed) debt.principalMinor else -debt.principalMinor,
            )
        }

    private fun repaymentRows(
        snapshot: StatementSnapshot,
        account: StatementAccount,
        debtsById: Map<String, DebtEntity>,
        counterpartyName: (String) -> String,
    ): List<StatementRow> = snapshot.debtRepayments
        .filter { it.accountId == account.id }
        .mapNotNull { repayment ->
            val debt = debtsById[repayment.debtId] ?: return@mapNotNull null
            val name = counterpartyName(debt.counterpartyId)
            val borrowed = debt.direction == DebtDirection.BORROWED
            StatementRow(
                accountId = account.id,
                accountName = account.name,
                fractionDigits = account.fractionDigits,
                occurredAt = repayment.occurredAt,
                localDate = repayment.localDate,
                description = if (borrowed) labels.repaidTo(name) else labels.repaidBy(name),
                categoryName = null,
                creditMinor = if (borrowed) null else repayment.amountMinor,
                debitMinor = if (borrowed) repayment.amountMinor else null,
                deltaMinor = if (borrowed) -repayment.amountMinor else repayment.amountMinor,
            )
        }

    private fun csv(
        rows: List<BalancedStatementRow>,
        columns: Set<StatementColumn>,
    ): String {
        val orderedColumns = StatementColumn.entries.filter { it in columns }
        val header = orderedColumns.joinToString(",") { column ->
            escape(headerFor(column))
        }
        val lines = ArrayList<String>(rows.size + 1)
        lines += header
        rows.forEach { balanced ->
            lines += orderedColumns.joinToString(",") { column ->
                fieldFor(column, balanced)
            }
        }
        return lines.joinToString("\r\n")
    }

    private fun headerFor(column: StatementColumn): String = when (column) {
        StatementColumn.ACCOUNT -> labels.columnAccount
        StatementColumn.DATE -> labels.columnDate
        StatementColumn.DESCRIPTION -> labels.columnDescription
        StatementColumn.CATEGORY -> labels.columnCategory
        StatementColumn.CREDIT -> labels.columnCredit
        StatementColumn.DEBIT -> labels.columnDebit
        StatementColumn.BALANCE -> labels.columnBalance
    }

    private fun fieldFor(
        column: StatementColumn,
        balanced: BalancedStatementRow,
    ): String = when (column) {
        StatementColumn.ACCOUNT -> escapeText(balanced.row.accountName)

        StatementColumn.DATE -> escape(balanced.row.localDate)
        StatementColumn.DESCRIPTION -> escapeText(balanced.row.description)
        StatementColumn.CATEGORY -> escapeText(balanced.row.categoryName.orEmpty())
        StatementColumn.CREDIT -> escape(
            balanced.row.creditMinor?.let { formatAmount(it, balanced.row.fractionDigits) }
                .orEmpty(),
        )

        StatementColumn.DEBIT -> escape(
            balanced.row.debitMinor?.let { formatAmount(it, balanced.row.fractionDigits) }
                .orEmpty(),
        )

        StatementColumn.BALANCE -> escape(
            formatAmount(
                balanced.balanceMinor,
                balanced.row.fractionDigits
            )
        )
    }

    private fun formatAmount(amountMinor: Long, fractionDigits: Int): String =
        BigDecimal.valueOf(amountMinor, fractionDigits).setScale(fractionDigits).toPlainString()

    private fun escape(value: String): String =
        if (value.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun escapeText(value: String): String =
        escape(if (value.firstOrNull() in FORMULA_TRIGGERS) "'$value" else value)

    private companion object {
        const val BOM = "\uFEFF"
        val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')
    }
}
