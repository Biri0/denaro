package it.rfmariano.denaro.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountInput
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.ui.theme.DenaroTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class EditorSubmissionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: DenaroDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        repository = FinanceRepository(database, clock = { 10 })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rapidAccountSaveCreatesOnlyOneAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completions = AtomicInteger()
        composeRule.setContent {
            DenaroTheme {
                AccountEditorScreen(
                    repository = repository,
                    accountId = null,
                    onFinished = { completions.incrementAndGet() },
                    onBack = {},
                    onMessage = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.name))
            .performTextInput("Cash")
        composeRule.onNodeWithText(context.getString(R.string.save))
            .performTouchInput { doubleClick() }

        composeRule.waitUntil { completions.get() == 1 }
        val accounts = runBlocking { database.accountDao().getAll() }
        assertEquals(1, accounts.size)
        assertEquals(0L, accounts.single().openingBalanceMinor)
    }

    @Test
    fun openingBalancePlaceholderAllowsImmediateAmountEntry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completions = AtomicInteger()
        composeRule.setContent {
            DenaroTheme {
                AccountEditorScreen(
                    repository = repository,
                    accountId = null,
                    onFinished = { completions.incrementAndGet() },
                    onBack = {},
                    onMessage = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.name))
            .performTextInput("Savings")
        composeRule.onNodeWithText(context.getString(R.string.opening_balance))
            .performTextInput("12.34")
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil { completions.get() == 1 }
        val account = runBlocking { database.accountDao().getAll().single() }
        assertEquals(1_234L, account.openingBalanceMinor)
    }

    @Test
    fun rapidTransactionSaveCreatesOnlyOneTransaction() = runBlocking {
        repository.createAccount(
            AccountInput(
                name = "Cash",
                description = null,
                openingBalanceMinor = 0,
                currency = "EUR",
            ),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completions = AtomicInteger()
        composeRule.setContent {
            DenaroTheme {
                ActivityEditorScreen(
                    repository = repository,
                    route = ActivityEditorRoute(kind = ActivityKind.EXPENSE),
                    onFinished = { completions.incrementAndGet() },
                    onBack = {},
                    onMessage = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Cash").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.amount))
            .performTextInput("10.00")
        composeRule.onNodeWithText(context.getString(R.string.save))
            .performTouchInput { doubleClick() }

        composeRule.waitUntil { completions.get() == 1 }
        assertEquals(1, database.transactionDao().count())
    }

    @Test
    fun transferWithSoleDestinationNeedsNoAccountSelection() = runBlocking {
        val sourceId = repository.createAccount(
            AccountInput(
                name = "Alpha",
                description = null,
                openingBalanceMinor = 0,
                currency = "EUR",
            ),
        )
        val destinationId = repository.createAccount(
            AccountInput(
                name = "Beta",
                description = null,
                openingBalanceMinor = 0,
                currency = "EUR",
            ),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completions = AtomicInteger()
        composeRule.setContent {
            DenaroTheme {
                ActivityEditorScreen(
                    repository = repository,
                    route = ActivityEditorRoute(kind = ActivityKind.TRANSFER),
                    onFinished = { completions.incrementAndGet() },
                    onBack = {},
                    onMessage = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithText("Beta").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.amount))
            .performTextInput("5.00")
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil { completions.get() == 1 }
        val usage = database.transferDao().getPairUsage().single()
        assertEquals(sourceId, usage.fromAccountId)
        assertEquals(destinationId, usage.toAccountId)
    }

    @Test
    fun failedAccountSaveUnlocksSaveForRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            DenaroTheme {
                AccountEditorScreen(
                    repository = repository,
                    accountId = null,
                    onFinished = {},
                    onBack = {},
                    onMessage = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()
        composeRule.onNodeWithText("Name is required").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.save)).assertIsEnabled()
    }
}
