package it.rfmariano.denaro.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun migration4To5PreservesBalancesAndAddsAdjustments() {
        helper.createDatabase(TEST_DATABASE, 4).apply {
            execSQL(
                """
                INSERT INTO accounts
                    (id, name, description, opening_balance_minor, currency, archived_at, created_at, updated_at)
                VALUES ('account-1', 'Cash', NULL, 1000, 'EUR', NULL, 10, 10)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 5, true, MIGRATION_4_5).use { db ->
            db.query("SELECT balance_minor FROM account_balances WHERE account_id = 'account-1'")
                .use {
                    it.moveToFirst()
                    assertEquals(1000L, it.getLong(0))
                }
            db.execSQL(
                """
                INSERT INTO balance_adjustments
                    (id, account_id, delta_minor, balance_before_minor, balance_after_minor,
                     occurred_at, local_date, created_at)
                VALUES ('adjustment-1', 'account-1', -250, 1000, 750, 20, '2026-08-20', 20)
                """.trimIndent(),
            )
            db.query("SELECT balance_minor FROM account_balances WHERE account_id = 'account-1'")
                .use {
                    it.moveToFirst()
                    assertEquals(750L, it.getLong(0))
                }
        }
    }

    @Test
    fun migration5To6ConvertsAmountsToNaturalFractionDigits() {
        helper.createDatabase(TEST_DATABASE, 5).apply {
            execSQL(
                """
                INSERT INTO accounts
                    (id, name, description, opening_balance_minor, currency, archived_at, created_at, updated_at)
                VALUES ('jpy-active', 'Yen cash', NULL, -12345, 'JPY', NULL, 10, 10),
                       ('jpy-archived', 'Old yen', NULL, 10050, 'JPY', 12, 11, 12),
                       ('kwd-active', 'Dinars', NULL, 123, 'KWD', NULL, 12, 12),
                       ('eur-untouched', 'Euro', NULL, 6789, 'EUR', NULL, 13, 13)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transactions
                    (id, account_id, recurring_rule_id, occurrence_key, amount_minor, type,
                     occurred_at, local_date, description, created_at, updated_at)
                VALUES ('jpy-tx', 'jpy-active', NULL, NULL, 150, 'EXPENSE', 14, '2026-08-10', 'train', 14, 14),
                       ('eur-tx', 'eur-untouched', NULL, NULL, 250, 'EXPENSE', 14, '2026-08-10', 'taxi', 14, 14)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transfers
                    (id, from_account_id, to_account_id, amount_minor, occurred_at, local_date,
                     description, created_at, updated_at)
                VALUES ('jpy-xfer', 'jpy-active', 'jpy-archived', 1000, 15, '2026-08-11', 'move', 15, 15)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO balance_adjustments
                    (id, account_id, delta_minor, balance_before_minor, balance_after_minor,
                     occurred_at, local_date, created_at)
                VALUES ('jpy-adj', 'jpy-active', -250, 9000, 8750, 16, '2026-08-12', 16)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO counterparties
                    (id, name, note, archived_at, created_at, updated_at)
                VALUES ('cp-1', 'Alex', NULL, NULL, 17, 17)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debts
                    (id, counterparty_id, account_id, direction, principal_minor, currency,
                     opened_at, local_date, due_date, note, created_at, updated_at)
                VALUES ('jpy-debt', 'cp-1', 'jpy-active', 'BORROWED', 5000, 'JPY',
                        18, '2026-08-13', NULL, NULL, 18, 18)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debt_repayments
                    (id, debt_id, account_id, amount_minor, occurred_at, local_date,
                     note, created_at, updated_at)
                VALUES ('jpy-rep', 'jpy-debt', 'jpy-active', 200, 19, '2026-08-14',
                        NULL, 19, 19)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 6, true, MIGRATION_5_6).use { db ->
            db.query(
                "SELECT id, opening_balance_minor, fraction_digits FROM accounts ORDER BY id",
            ).use { cursor ->
                listOf(
                    Triple("eur-untouched", 6789L, 2),
                    Triple("jpy-active", -123L, 0),
                    Triple("jpy-archived", 100L, 0),
                    Triple("kwd-active", 1230L, 3),
                ).forEachIndexed { index, (id, balance, scale) ->
                    assertTrue(cursor.moveToPosition(index))
                    assertEquals(id, cursor.getString(0))
                    assertEquals(balance, cursor.getLong(1))
                    assertEquals(scale, cursor.getInt(2))
                }
            }
            db.query(
                "SELECT id, amount_minor FROM transactions ORDER BY id",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("eur-tx", cursor.getString(0))
                assertEquals(250L, cursor.getLong(1))
                assertTrue(cursor.moveToNext())
                assertEquals("jpy-tx", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
            }
            db.query(
                "SELECT amount_minor FROM transfers WHERE id = 'jpy-xfer'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(10L, it.getLong(0))
            }
            db.query(
                "SELECT delta_minor, balance_before_minor, balance_after_minor " +
                        "FROM balance_adjustments WHERE id = 'jpy-adj'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(-2L, it.getLong(0))
                assertEquals(90L, it.getLong(1))
                assertEquals(87L, it.getLong(2))
            }
            db.query(
                "SELECT principal_minor FROM debts WHERE id = 'jpy-debt'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(50L, it.getLong(0))
            }
            db.query(
                "SELECT amount_minor FROM debt_repayments WHERE id = 'jpy-rep'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(2L, it.getLong(0))
            }
            db.query(
                "SELECT balance_minor, fraction_digits " +
                        "FROM account_balances WHERE account_id = 'jpy-active'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(-88L, cursor.getLong(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
