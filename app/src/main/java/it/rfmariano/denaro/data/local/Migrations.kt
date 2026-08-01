package it.rfmariano.denaro.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
