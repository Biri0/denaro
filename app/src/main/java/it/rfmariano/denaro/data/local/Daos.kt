package it.rfmariano.denaro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts ORDER BY created_at, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY created_at, id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}

@Dao
interface RecurringRuleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rules: List<RecurringRuleEntity>)

    @Query(
        """
        SELECT * FROM recurring_rules
        WHERE is_active = 1
        ORDER BY next_occurrence_at, id
        """,
    )
    fun observeActive(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT COUNT(*) FROM recurring_rules")
    suspend fun count(): Int
}

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transfers: List<TransferEntity>)

    @Query("SELECT * FROM transfers ORDER BY occurred_at DESC, id DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT COUNT(*) FROM transfers")
    suspend fun count(): Int
}

@Dao
interface AccountBalanceDao {
    @Query("SELECT * FROM account_balances ORDER BY account_id")
    fun observeAll(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM account_balances ORDER BY account_id")
    suspend fun getAll(): List<AccountBalance>
}

@Dao
interface LegacyImportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: LegacyImportEntity)

    @Update
    suspend fun update(record: LegacyImportEntity)

    @Query("SELECT * FROM legacy_imports WHERE source = :source")
    suspend fun get(source: String): LegacyImportEntity?
}
