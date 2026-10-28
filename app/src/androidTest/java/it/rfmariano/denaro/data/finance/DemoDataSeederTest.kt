package it.rfmariano.denaro.data.finance

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.BalanceAdjustmentEntity
import it.rfmariano.denaro.data.local.BudgetEntity
import it.rfmariano.denaro.data.local.DenaroDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class DemoDataSeederTest {
    @Test
    fun resetReplacesDemoDataWithoutTouchingRealDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val real = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        val demo = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            real.accountDao().insert(
                AccountEntity(
                    id = "real-sentinel",
                    name = "Private account",
                    description = null,
                    openingBalanceMinor = 1,
                    currency = "EUR",
                    archivedAt = null,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            )
            val seeder = DemoDataSeeder(demo)
            seeder.reset(LocalDate.of(2026, 8, 1), ZoneId.of("Europe/Rome"), italian = false)
            demo.balanceAdjustmentDao().insert(
                BalanceAdjustmentEntity(
                    id = "demo-adjustment-reset-check",
                    accountId = "demo-account-checking",
                    deltaMinor = -100,
                    balanceBeforeMinor = 100,
                    balanceAfterMinor = 0,
                    occurredAt = 1,
                    localDate = "2026-08-01",
                    createdAt = 1,
                ),
            )
            val originalDemoCount = demo.transactionDao().count()
            demo.transactionDao().delete(demo.transactionDao().getById("demo-transaction-0000")!!)

            assertNotEquals(originalDemoCount, demo.transactionDao().count())
            seeder.reset(LocalDate.of(2026, 8, 1), ZoneId.of("Europe/Rome"), italian = true)

            assertEquals(originalDemoCount, demo.transactionDao().count())
            assertNull(
                demo.balanceAdjustmentDao().getById("demo-adjustment-reset-check"),
            )
            assertEquals(listOf("real-sentinel"), real.accountDao().getAll().map { it.id })
            assertEquals(
                "Conto quotidiano",
                demo.accountDao().getById("demo-account-checking")?.name
            )
        } finally {
            demo.close()
            real.close()
        }
    }

    @Test
    fun resetClearsBudgetsLeftBehindByAPreviousDemoSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val referenceDate = LocalDate.of(2026, 8, 1)
            val zone = ZoneId.of("Europe/Rome")
            val fixture = demoFixture(referenceDate, zone, italian = false)
            val seeder = DemoDataSeeder(database)
            seeder.reset(referenceDate, zone, italian = false)

            // Budgets reference categories with ON DELETE RESTRICT: a budget the reset does not
            // clear makes the whole reset fail, which leaves demo mode permanently unusable. The
            // category is one the fixture leaves unbudgeted, so the insert stays unique.
            val unbudgetedCategory = fixture.categories.first { category ->
                fixture.budgets.none { it.categoryId == category.id }
            }
            database.budgetDao().insert(
                BudgetEntity(
                    id = "demo-budget-created-outside-the-fixture",
                    categoryId = unbudgetedCategory.id,
                    currency = "EUR",
                    amountMinor = 1_000,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            )

            seeder.reset(referenceDate, zone, italian = false)

            assertNull(
                database.budgetDao().getById("demo-budget-created-outside-the-fixture"),
            )
            assertEquals(
                fixture.budgets.map { it.id }.toSet(),
                database.backupDao().budgets().map { it.id }.toSet(),
            )
        } finally {
            database.close()
        }
    }

    /**
     * Reproduces the marketing-asset drift: the fixture is seeded for a fixed reference date, but
     * recurrences were processed against the wall clock, so every month between the reference date
     * and "today" appended salary/rent/subscription rows. `--date 2026-09-30` showed €8,528.58 on
     * September 30 and €10,443.59 once October had passed.
     */
    @Test
    fun processingRecurrencesUsesTheReferenceDateInsteadOfTheWallClock() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val referenceDate = LocalDate.of(2026, 9, 30)
            val zone = ZoneId.of("Europe/Rome")
            DemoDataSeeder(database).reset(
                referenceDate,
                zone,
                language = DemoLanguage.ENGLISH,
            )
            val repository = FinanceRepository(database)

            suspend fun availableMinor(): Long {
                val savingsIds = database.accountDao().getAll()
                    .filter { it.isSavings }
                    .mapTo(mutableSetOf()) { it.id }
                return database.accountBalanceDao().getAll()
                    .filterNot { it.accountId in savingsIds }
                    .sumOf { it.balanceMinor }
            }

            repository.processDueRecurrences(
                referenceDate.atTime(LocalTime.MAX).atZone(zone).toInstant(),
            )

            assertTrue(
                database.backupDao().transactions().none {
                    LocalDate.parse(it.localDate).isAfter(referenceDate)
                },
            )
            assertEquals(852_858L, availableMinor())

            // The same fixture processed against a later cutoff still drifts: this is what the
            // marketing script must never do, and why demoSession pins the cutoff to --date.
            repository.processDueRecurrences(
                LocalDate.of(2026, 10, 28).atTime(LocalTime.MAX).atZone(zone).toInstant(),
            )
            assertEquals(
                listOf("2026-10-02", "2026-10-20", "2026-10-27"),
                database.backupDao().transactions()
                    .map { it.localDate }
                    .filter { LocalDate.parse(it).isAfter(referenceDate) }
                    .sorted(),
            )
            assertEquals(1_044_359L, availableMinor())
        } finally {
            database.close()
        }
    }
}
