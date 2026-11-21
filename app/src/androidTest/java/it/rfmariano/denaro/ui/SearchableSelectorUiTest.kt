package it.rfmariano.denaro.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountInput
import it.rfmariano.denaro.data.finance.CategorySummary
import it.rfmariano.denaro.data.finance.CounterpartyInput
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.ui.theme.DenaroTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SearchableSelectorUiTest {
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
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {}
        }
        composeRule.waitForIdle()
        database.close()
    }

    @Test
    fun debtEditorUsesDropdownWithoutSearchWhenTenOrFewerCounterparties() = runBlocking {
        repository.createAccount(AccountInput("Cash", null, 0, "EUR"))
        repeat(10) { index ->
            repository.createCounterparty(CounterpartyInput("Person ${index + 1}", null))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            DenaroTheme {
                DebtEditorScreen(
                    repository = repository,
                    debtId = null,
                    onBack = {},
                    onFinished = {},
                    onDeleted = {},
                    onMessage = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Person 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.counterparty)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.search)).assertDoesNotExist()
        composeRule.onNodeWithText("Person 10").performClick()
        composeRule.onNodeWithText("Person 10").assertExists()
        Unit
    }

    @Test
    fun debtEditorOpensSearchableSheetWhenMoreThanTenCounterparties() = runBlocking {
        repository.createAccount(AccountInput("Cash", null, 0, "EUR"))
        repeat(11) { index ->
            val note = if (index == 10) "Landlord" else null
            repository.createCounterparty(CounterpartyInput("Person ${index + 1}", note))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completions = AtomicInteger()

        composeRule.setContent {
            DenaroTheme {
                DebtEditorScreen(
                    repository = repository,
                    debtId = null,
                    onBack = {},
                    onFinished = { completions.incrementAndGet() },
                    onDeleted = {},
                    onMessage = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Person 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.counterparty)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.search)).performTextInput("landlord")
        composeRule.onNodeWithText("Person 11").performClick()
        composeRule.onNodeWithText(context.getString(R.string.amount)).performTextInput("25.00")
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil { completions.get() == 1 }
        val debt = repository.observeDebts().first().single()
        assertEquals("Person 11", debt.counterpartyName)
    }

    @Test
    fun debtEditorSearchSheetPrefillsNewCounterpartyDialogFromQuery() = runBlocking {
        repository.createAccount(AccountInput("Cash", null, 0, "EUR"))
        repeat(11) { index ->
            repository.createCounterparty(CounterpartyInput("Person ${index + 1}", null))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            DenaroTheme {
                DebtEditorScreen(
                    repository = repository,
                    debtId = null,
                    onBack = {},
                    onFinished = {},
                    onDeleted = {},
                    onMessage = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Person 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.counterparty)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.search))
            .performTextInput("Marco Rossi")
        composeRule.onNodeWithText(context.getString(R.string.no_matching_results)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.add_counterparty)).performClick()
        composeRule.onNodeWithText("Marco Rossi").assertExists()
        Unit
    }

    @Test
    fun categorySelectorOpensSearchableSheetWhenMoreThanTenCategories() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val categories = buildList {
            add(category("food", null, "Food"))
            add(category("groceries", "food", "Groceries"))
            repeat(9) { index ->
                add(category("cat-$index", null, "Category ${index + 1}"))
            }
        }
        var selectedId by mutableStateOf<String?>(null)

        composeRule.setContent {
            DenaroTheme {
                CategorySelector(
                    categories = categories,
                    selectedId = selectedId,
                    onSelected = { selectedId = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.category)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.search)).performTextInput("groc")
        composeRule.onNodeWithText("Groceries").performClick()

        assertEquals("groceries", selectedId)
    }

    private fun category(
        id: String,
        parentId: String?,
        name: String,
    ) = CategorySummary(
        id = id,
        type = TransactionType.EXPENSE,
        parentId = parentId,
        name = name,
        iconName = "shapes",
        colorIndex = 0,
        archivedAt = null,
    )
}
