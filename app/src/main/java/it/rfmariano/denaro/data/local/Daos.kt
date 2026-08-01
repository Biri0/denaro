package it.rfmariano.denaro.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts ORDER BY created_at, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query(
        """
        SELECT * FROM accounts
        WHERE archived_at IS NULL
        ORDER BY name COLLATE NOCASE, created_at, id
        """,
    )
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY created_at, id")
    suspend fun getAll(): List<AccountEntity>

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET archived_at = :archivedAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archivedAt: Long?, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query(
        """
        SELECT * FROM categories
        ORDER BY type, parent_id IS NOT NULL, name COLLATE NOCASE, created_at, id
        """,
    )
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type, name COLLATE NOCASE")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Update
    suspend fun update(category: CategoryEntity)

    @Query(
        """
        UPDATE categories
        SET color_index = :colorIndex,
            updated_at = :updatedAt
        WHERE parent_id = :parentId
        """,
    )
    suspend fun updateChildrenColor(parentId: String, colorIndex: Int, updatedAt: Long)

    @Query(
        """
        UPDATE categories
        SET archived_at = :archivedAt,
            archived_by_parent_id = NULL,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun setArchived(id: String, archivedAt: Long?, updatedAt: Long)

    @Query(
        """
        UPDATE categories
        SET archived_at = :archivedAt,
            archived_by_parent_id = :parentId,
            updated_at = :updatedAt
        WHERE parent_id = :parentId AND archived_at IS NULL
        """,
    )
    suspend fun archiveActiveChildren(parentId: String, archivedAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE categories
        SET archived_at = NULL,
            archived_by_parent_id = NULL,
            updated_at = :updatedAt
        WHERE archived_by_parent_id = :parentId
        """,
    )
    suspend fun restoreChildrenArchivedByParent(parentId: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

}

@Dao
interface RecurringRuleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RecurringRuleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rules: List<RecurringRuleEntity>)

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: String): RecurringRuleEntity?

    @Query(
        """
        SELECT * FROM recurring_rules
        WHERE is_active = 1
        ORDER BY next_occurrence_at, id
        """,
    )
    fun observeActive(): Flow<List<RecurringRuleEntity>>

    @Query(
        """
        SELECT * FROM recurring_rules
        WHERE account_id = :accountId
        ORDER BY is_active DESC, next_occurrence_at, id
        """,
    )
    fun observeForAccount(accountId: String): Flow<List<RecurringRuleEntity>>

    @Query(
        """
        SELECT * FROM recurring_rules
        WHERE is_active = 1 AND next_occurrence_at <= :now
        ORDER BY next_occurrence_at, id
        """,
    )
    suspend fun getDue(now: Long): List<RecurringRuleEntity>

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    @Query(
        """
        UPDATE recurring_rules
        SET is_active = 0, updated_at = :updatedAt
        WHERE account_id = :accountId AND is_active = 1
        """,
    )
    suspend fun deactivateForAccount(accountId: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM recurring_rules")
    suspend fun count(): Int

}

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: TransferEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transfers: List<TransferEntity>)

    @Query("SELECT * FROM transfers ORDER BY occurred_at DESC, id DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getById(id: String): TransferEntity?

    @Query(
        """
        SELECT from_account_id AS fromAccountId,
               to_account_id AS toAccountId,
               COUNT(*) AS transferCount,
               MAX(occurred_at) AS lastOccurredAt
        FROM transfers
        GROUP BY from_account_id, to_account_id
        """,
    )
    suspend fun getPairUsage(): List<TransferPairUsage>

    @Update
    suspend fun update(transfer: TransferEntity)

    @Delete
    suspend fun delete(transfer: TransferEntity)

    @Query("SELECT COUNT(*) FROM transfers")
    suspend fun count(): Int

}

data class TransferPairUsage(
    val fromAccountId: String,
    val toAccountId: String,
    val transferCount: Long,
    val lastOccurredAt: Long,
)

@Dao
interface AccountBalanceDao {
    @Query("SELECT * FROM account_balances ORDER BY account_id")
    fun observeAll(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM account_balances WHERE account_id = :accountId")
    fun observeByAccount(accountId: String): Flow<AccountBalance?>

    @Query("SELECT * FROM account_balances ORDER BY account_id")
    suspend fun getAll(): List<AccountBalance>
}

data class ActivityRecord(
    val id: String,
    val kind: String,
    val accountId: String,
    val counterpartyAccountId: String?,
    val accountName: String,
    val counterpartyAccountName: String?,
    val currency: String,
    val amountMinor: Long,
    val transactionType: String?,
    val occurredAt: Long,
    val localDate: String,
    val description: String?,
    val recurringRuleId: String?,
    val categoryId: String?,
    val categoryParentId: String?,
    val categoryName: String?,
    val categoryIconName: String?,
    val categoryColorIndex: Int?,
)

data class AnalyticsRecord(
    val amountMinor: Long,
    val transactionType: String,
    val localDate: String,
    val categoryId: String?,
    val categoryParentId: String?,
)

@Dao
interface ActivityDao {
    @Query(
        """
        SELECT * FROM (
            SELECT transactions.id AS id,
                   transactions.type AS kind,
                   transactions.account_id AS accountId,
                   NULL AS counterpartyAccountId,
                   accounts.name AS accountName,
                   NULL AS counterpartyAccountName,
                   accounts.currency AS currency,
                   transactions.amount_minor AS amountMinor,
                   transactions.type AS transactionType,
                   transactions.occurred_at AS occurredAt,
                   transactions.local_date AS localDate,
                   transactions.description AS description,
                   transactions.recurring_rule_id AS recurringRuleId,
                   transactions.category_id AS categoryId,
                   categories.parent_id AS categoryParentId,
                   categories.name AS categoryName,
                   categories.icon_name AS categoryIconName,
                   categories.color_index AS categoryColorIndex
            FROM transactions
            JOIN accounts ON accounts.id = transactions.account_id
            LEFT JOIN categories ON categories.id = transactions.category_id
            UNION ALL
            SELECT transfers.id AS id,
                   'TRANSFER' AS kind,
                   transfers.from_account_id AS accountId,
                   transfers.to_account_id AS counterpartyAccountId,
                   source.name AS accountName,
                   target.name AS counterpartyAccountName,
                   source.currency AS currency,
                   transfers.amount_minor AS amountMinor,
                   NULL AS transactionType,
                   transfers.occurred_at AS occurredAt,
                   transfers.local_date AS localDate,
                   transfers.description AS description,
                   NULL AS recurringRuleId,
                   NULL AS categoryId,
                   NULL AS categoryParentId,
                   NULL AS categoryName,
                   NULL AS categoryIconName,
                   NULL AS categoryColorIndex
            FROM transfers
            JOIN accounts AS source ON source.id = transfers.from_account_id
            JOIN accounts AS target ON target.id = transfers.to_account_id
        )
        WHERE (:kind IS NULL OR kind = :kind)
          AND (
              :accountId IS NULL OR
              accountId = :accountId OR
              counterpartyAccountId = :accountId
          )
          AND (:currency IS NULL OR currency = :currency)
          AND (:fromDate IS NULL OR localDate >= :fromDate)
          AND (:toDate IS NULL OR localDate < :toDate)
          AND (
              :categoryId IS NULL OR
              (:categoryId = '__uncategorized__' AND categoryId IS NULL) OR
              categoryId = :categoryId OR
              categoryParentId = :categoryId
          )
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun pagingSource(
        kind: String?,
        accountId: String?,
        currency: String?,
        categoryId: String?,
        fromDate: String?,
        toDate: String?,
    ): PagingSource<Int, ActivityRecord>

    @Query(
        """
        SELECT transactions.amount_minor AS amountMinor,
               transactions.type AS transactionType,
               transactions.local_date AS localDate,
               transactions.category_id AS categoryId,
               categories.parent_id AS categoryParentId
        FROM transactions
        JOIN accounts ON accounts.id = transactions.account_id
        LEFT JOIN categories ON categories.id = transactions.category_id
        WHERE transactions.local_date >= :fromDate
          AND transactions.local_date < :toDate
          AND accounts.currency = :currency
          AND (:accountId IS NULL OR accounts.id = :accountId)
        ORDER BY transactions.local_date, transactions.id
        """,
    )
    fun observeAnalytics(
        fromDate: String,
        toDate: String,
        currency: String,
        accountId: String?,
    ): Flow<List<AnalyticsRecord>>
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
