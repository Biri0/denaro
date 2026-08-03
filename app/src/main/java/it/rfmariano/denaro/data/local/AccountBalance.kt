package it.rfmariano.denaro.data.local

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

internal const val ACCOUNT_BALANCE_QUERY = """WITH account_movements(account_id, amount_minor) AS (
SELECT account_id,
       CASE type WHEN 'INCOME' THEN amount_minor ELSE -amount_minor END
FROM transactions
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
