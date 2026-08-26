package it.rfmariano.denaro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        RecurringRuleEntity::class,
        TransactionEntity::class,
        BalanceAdjustmentEntity::class,
        TransferEntity::class,
        CounterpartyEntity::class,
        DebtEntity::class,
        DebtRepaymentEntity::class,
        LegacyImportEntity::class,
    ],
    views = [AccountBalance::class],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DenaroDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun balanceAdjustmentDao(): BalanceAdjustmentDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun transferDao(): TransferDao
    abstract fun counterpartyDao(): CounterpartyDao
    abstract fun debtDao(): DebtDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun activityDao(): ActivityDao
    abstract fun legacyImportDao(): LegacyImportDao
    abstract fun backupDao(): BackupDao
}
