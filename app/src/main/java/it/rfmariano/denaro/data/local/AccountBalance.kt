package it.rfmariano.denaro.data.local

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

internal const val ACCOUNT_BALANCE_QUERY = """WITH account_movements(account_id, amount_minor) AS (
SELECT account_id,
       CASE type WHEN 'INCOME' THEN amount_minor ELSE -amount_minor END
FROM transactions
UNION ALL
SELECT account_id, delta_minor
FROM balance_adjustments
UNION ALL
SELECT from_account_id, -amount_minor
FROM transfers
UNION ALL
SELECT to_account_id, amount_minor
FROM transfers
UNION ALL
SELECT account_id,
       CASE direction WHEN 'BORROWED' THEN principal_minor ELSE -principal_minor END
FROM debts
UNION ALL
SELECT debt_repayments.account_id,
       CASE debts.direction WHEN 'BORROWED' THEN -debt_repayments.amount_minor ELSE debt_repayments.amount_minor END
FROM debt_repayments
JOIN debts ON debts.id = debt_repayments.debt_id
)
SELECT accounts.id AS account_id,
       accounts.currency AS currency,
       accounts.opening_balance_minor +
           COALESCE(SUM(account_movements.amount_minor), 0) AS balance_minor
FROM accounts
LEFT JOIN account_movements ON account_movements.account_id = accounts.id
GROUP BY accounts.id, accounts.currency, accounts.opening_balance_minor"""

@DatabaseView(
    viewName = "account_balances",
    value = ACCOUNT_BALANCE_QUERY,
)
data class AccountBalance(
    @ColumnInfo(name = "account_id") val accountId: String,
    val currency: String,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
)

internal const val ACCOUNT_WITH_BALANCE_QUERY = """SELECT accounts.*,
       accounts.opening_balance_minor
       + COALESCE((SELECT SUM(CASE type WHEN 'INCOME' THEN amount_minor ELSE -amount_minor END) FROM transactions WHERE account_id = accounts.id), 0)
       + COALESCE((SELECT SUM(delta_minor) FROM balance_adjustments WHERE account_id = accounts.id), 0)
       + COALESCE((SELECT SUM(-amount_minor) FROM transfers WHERE from_account_id = accounts.id), 0)
       + COALESCE((SELECT SUM(amount_minor) FROM transfers WHERE to_account_id = accounts.id), 0)
       + COALESCE((SELECT SUM(CASE direction WHEN 'BORROWED' THEN principal_minor ELSE -principal_minor END) FROM debts WHERE account_id = accounts.id), 0)
       + COALESCE((SELECT SUM(CASE debts.direction WHEN 'BORROWED' THEN -debt_repayments.amount_minor ELSE debt_repayments.amount_minor END)
                   FROM debt_repayments JOIN debts ON debts.id = debt_repayments.debt_id
                   WHERE debt_repayments.account_id = accounts.id), 0) AS balanceMinor
FROM accounts"""

data class AccountWithBalance(
    val id: String,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "opening_balance_minor") val openingBalanceMinor: Long,
    val currency: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val balanceMinor: Long,
)
