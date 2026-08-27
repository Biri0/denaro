package it.rfmariano.denaro.data.local

import androidx.room.Dao
import androidx.room.Query

@Dao
interface StatementDao {
    @Query("SELECT * FROM transactions WHERE account_id IN (:accountIds) ORDER BY occurred_at, id")
    suspend fun transactions(accountIds: List<String>): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transfers
        WHERE from_account_id IN (:accountIds) OR to_account_id IN (:accountIds)
        ORDER BY occurred_at, id
        """,
    )
    suspend fun transfers(accountIds: List<String>): List<TransferEntity>

    @Query("SELECT * FROM balance_adjustments WHERE account_id IN (:accountIds) ORDER BY occurred_at, id")
    suspend fun balanceAdjustments(accountIds: List<String>): List<BalanceAdjustmentEntity>

    @Query("SELECT * FROM debts WHERE account_id IN (:accountIds) ORDER BY opened_at, id")
    suspend fun debts(accountIds: List<String>): List<DebtEntity>

    @Query("SELECT * FROM debt_repayments WHERE account_id IN (:accountIds) ORDER BY occurred_at, id")
    suspend fun debtRepayments(accountIds: List<String>): List<DebtRepaymentEntity>
}
