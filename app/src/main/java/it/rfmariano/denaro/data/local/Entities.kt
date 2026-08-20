package it.rfmariano.denaro.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType {
    INCOME,
    EXPENSE,
}

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

enum class LegacyImportStatus {
    PENDING_VALIDATION,
    COMPLETE,
}

enum class DebtDirection {
    BORROWED,
    LENT,
}

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["parent_id"]),
        Index(value = ["type", "archived_at", "name"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val type: TransactionType,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    val name: String,
    @ColumnInfo(name = "icon_name") val iconName: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "archived_by_parent_id") val archivedByParentId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["archived_at", "name"])],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "opening_balance_minor") val openingBalanceMinor: Long,
    val currency: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["is_active", "next_occurrence_at"]),
    ],
)
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "transaction_type") val transactionType: TransactionType,
    val description: String?,
    val frequency: RecurrenceFrequency,
    @ColumnInfo(name = "interval_count") val intervalCount: Int,
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
    @ColumnInfo(name = "anchor_day") val anchorDay: Int?,
    @ColumnInfo(name = "anchor_month") val anchorMonth: Int?,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "last_generated_at") val lastGeneratedAt: Long?,
    @ColumnInfo(name = "next_occurrence_at") val nextOccurrenceAt: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurring_rule_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["account_id", "occurred_at"]),
        Index(value = ["recurring_rule_id"]),
        Index(value = ["category_id"]),
        Index(value = ["local_date", "type", "account_id"]),
        Index(value = ["recurring_rule_id", "occurrence_key"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "recurring_rule_id") val recurringRuleId: String?,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "occurrence_key") val occurrenceKey: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val type: TransactionType,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    val description: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "balance_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["account_id", "occurred_at"])],
)
data class BalanceAdjustmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "delta_minor") val deltaMinor: Long,
    @ColumnInfo(name = "balance_before_minor") val balanceBeforeMinor: Long,
    @ColumnInfo(name = "balance_after_minor") val balanceAfterMinor: Long,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["from_account_id", "occurred_at"]),
        Index(value = ["to_account_id", "occurred_at"]),
    ],
)
data class TransferEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "from_account_id") val fromAccountId: String,
    @ColumnInfo(name = "to_account_id") val toAccountId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    val description: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "counterparties",
    indices = [Index(value = ["archived_at", "name"])],
)
data class CounterpartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String?,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = CounterpartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterparty_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["counterparty_id"]),
        Index(value = ["account_id"]),
        Index(value = ["due_date"]),
    ],
)
data class DebtEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "counterparty_id") val counterpartyId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    val direction: DebtDirection,
    @ColumnInfo(name = "principal_minor") val principalMinor: Long,
    val currency: String,
    @ColumnInfo(name = "opened_at") val openedAt: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "debt_repayments",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["debt_id"]), Index(value = ["account_id", "occurred_at"])],
)
data class DebtRepaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "legacy_imports")
data class LegacyImportEntity(
    @PrimaryKey val source: String,
    val status: LegacyImportStatus,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "account_count") val accountCount: Int,
    @ColumnInfo(name = "transaction_count") val transactionCount: Int,
    @ColumnInfo(name = "transfer_count") val transferCount: Int,
    @ColumnInfo(name = "recurring_rule_count") val recurringRuleCount: Int,
    @ColumnInfo(name = "warnings_json") val warningsJson: String,
)
