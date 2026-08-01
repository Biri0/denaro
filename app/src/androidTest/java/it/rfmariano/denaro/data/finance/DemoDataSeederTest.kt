package it.rfmariano.denaro.data.finance

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.data.local.AccountEntity
import it.rfmariano.denaro.data.local.DenaroDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
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
            val originalDemoCount = demo.transactionDao().count()
            demo.transactionDao().delete(demo.transactionDao().getById("demo-transaction-0000")!!)

            assertNotEquals(originalDemoCount, demo.transactionDao().count())
            seeder.reset(LocalDate.of(2026, 8, 1), ZoneId.of("Europe/Rome"), italian = true)

            assertEquals(originalDemoCount, demo.transactionDao().count())
            assertEquals(listOf("real-sentinel"), real.accountDao().getAll().map { it.id })
            assertEquals("Conto quotidiano", demo.accountDao().getById("demo-account-checking")?.name)
        } finally {
            demo.close()
            real.close()
        }
    }
}
