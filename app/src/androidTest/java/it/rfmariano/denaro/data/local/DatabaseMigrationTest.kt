package it.rfmariano.denaro.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DenaroDatabase::class.java,
    )

    @After
    fun cleanUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        java.io.File(context.getDatabasePath(TEST_DATABASE).path + ".lck").delete()
    }

    @Test
    fun migration1To2PreservesExistingFinanceData() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO accounts
                    (id, name, description, opening_balance_minor, currency, archived_at, created_at, updated_at)
                VALUES ('account-1', 'Cash', NULL, 12345, 'EUR', NULL, 10, 10)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transactions
                    (id, account_id, recurring_rule_id, occurrence_key, amount_minor, type,
                     occurred_at, local_date, description, created_at, updated_at)
                VALUES ('transaction-1', 'account-1', NULL, NULL, 500, 'EXPENSE',
                        20, '2026-07-31', 'Existing expense', 20, 20)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT name, opening_balance_minor FROM accounts WHERE id = 'account-1'")
                .use {
                    it.moveToFirst()
                    assertEquals("Cash", it.getString(0))
                    assertEquals(12345L, it.getLong(1))
                }
            db.query("SELECT amount_minor, description, category_id FROM transactions WHERE id = 'transaction-1'")
                .use {
                    it.moveToFirst()
                    assertEquals(500L, it.getLong(0))
                    assertEquals("Existing expense", it.getString(1))
                    assertNull(if (it.isNull(2)) null else it.getString(2))
                }
            db.query("SELECT COUNT(*) FROM categories").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun migration2To3AlignsExistingSubcategoryColors() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                """
                INSERT INTO categories
                    (id, type, parent_id, name, icon_name, color_index, archived_at,
                     archived_by_parent_id, created_at, updated_at)
                VALUES ('parent', 'EXPENSE', NULL, 'Food', 'utensils', 4, NULL, NULL, 10, 10)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO categories
                    (id, type, parent_id, name, icon_name, color_index, archived_at,
                     archived_by_parent_id, created_at, updated_at)
                VALUES ('child', 'EXPENSE', 'parent', 'Groceries', 'basket', 9, NULL, NULL, 10, 10)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT color_index FROM categories WHERE id = 'child'").use {
                it.moveToFirst()
                assertEquals(4, it.getInt(0))
            }
        }
    }

    @Test
    fun migration3To4PreservesBalancesAndAddsDebtLedger() {
        helper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL(
                """
                INSERT INTO accounts
                    (id, name, description, opening_balance_minor, currency, archived_at, created_at, updated_at)
                VALUES ('account-1', 'Cash', NULL, 1000, 'EUR', NULL, 10, 10)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT balance_minor FROM account_balances WHERE account_id = 'account-1'")
                .use {
                    it.moveToFirst()
                    assertEquals(1000L, it.getLong(0))
                }
            db.query("SELECT COUNT(*) FROM debts").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM counterparties").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
