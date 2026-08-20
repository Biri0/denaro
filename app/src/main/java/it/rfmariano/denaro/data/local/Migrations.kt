package it.rfmariano.denaro.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val ACCOUNT_BALANCE_QUERY_V4 =
    """WITH account_movements(account_id, amount_minor) AS (
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

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `parent_id` TEXT,
                `name` TEXT NOT NULL,
                `icon_name` TEXT NOT NULL,
                `color_index` INTEGER NOT NULL,
                `archived_at` INTEGER,
                `archived_by_parent_id` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`parent_id`) REFERENCES `categories`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_parent_id` ON `categories` (`parent_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_categories_type_archived_at_name` " +
                    "ON `categories` (`type`, `archived_at`, `name`)",
        )
        db.execSQL("ALTER TABLE `recurring_rules` ADD COLUMN `category_id` TEXT REFERENCES `categories`(`id`) ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_category_id` ON `recurring_rules` (`category_id`)")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `category_id` TEXT REFERENCES `categories`(`id`) ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_category_id` ON `transactions` (`category_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_local_date_type_account_id` " +
                    "ON `transactions` (`local_date`, `type`, `account_id`)",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE categories
            SET color_index = (
                SELECT parent.color_index
                FROM categories AS parent
                WHERE parent.id = categories.parent_id
            )
            WHERE parent_id IS NOT NULL
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `counterparties` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `note` TEXT,
                `archived_at` INTEGER, `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_counterparties_archived_at_name` ON `counterparties` (`archived_at`, `name`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `debts` (
                `id` TEXT NOT NULL, `counterparty_id` TEXT NOT NULL,
                `account_id` TEXT NOT NULL, `direction` TEXT NOT NULL,
                `principal_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL,
                `opened_at` INTEGER NOT NULL, `local_date` TEXT NOT NULL,
                `due_date` TEXT, `note` TEXT, `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`counterparty_id`) REFERENCES `counterparties`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_counterparty_id` ON `debts` (`counterparty_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_account_id` ON `debts` (`account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_due_date` ON `debts` (`due_date`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `debt_repayments` (
                `id` TEXT NOT NULL, `debt_id` TEXT NOT NULL, `account_id` TEXT NOT NULL,
                `amount_minor` INTEGER NOT NULL, `occurred_at` INTEGER NOT NULL,
                `local_date` TEXT NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_repayments_debt_id` ON `debt_repayments` (`debt_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_repayments_account_id_occurred_at` ON `debt_repayments` (`account_id`, `occurred_at`)")
        db.execSQL("DROP VIEW IF EXISTS `account_balances`")
        db.execSQL("CREATE VIEW `account_balances` AS $ACCOUNT_BALANCE_QUERY_V4")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `balance_adjustments` (
                `id` TEXT NOT NULL,
                `account_id` TEXT NOT NULL,
                `delta_minor` INTEGER NOT NULL,
                `balance_before_minor` INTEGER NOT NULL,
                `balance_after_minor` INTEGER NOT NULL,
                `occurred_at` INTEGER NOT NULL,
                `local_date` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_balance_adjustments_account_id_occurred_at` " +
                    "ON `balance_adjustments` (`account_id`, `occurred_at`)",
        )
        db.execSQL("DROP VIEW IF EXISTS `account_balances`")
        db.execSQL("CREATE VIEW `account_balances` AS $ACCOUNT_BALANCE_QUERY")
    }
}
