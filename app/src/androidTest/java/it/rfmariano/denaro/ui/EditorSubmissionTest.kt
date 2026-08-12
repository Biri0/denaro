package it.rfmariano.denaro.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountInput
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.CategoryInput
import it.rfmariano.denaro.data.finance.CounterpartyInput
import it.rfmariano.denaro.data.finance.DebtInput
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.TransactionInput
import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.ui.theme.DenaroTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {}
        }
        composeRule.waitForIdle()
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

    @Test
    fun dueDateClearIconRemovesDate() = runBlocking {
        val accountId = repository.createAccount(
            AccountInput("Cash", null, 0, "EUR"),
        )
        val counterpartyId = repository.createCounterparty(
            CounterpartyInput("Alex", null),
        )
        val debtId = repository.createDebt(
            DebtInput(
                counterpartyId = counterpartyId,
                accountId = accountId,
                direction = DebtDirection.BORROWED,
                principalMinor = 1_000,
                openedAt = 1,
                dueDate = "2026-08-12",
                note = null,
            ),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completions = AtomicInteger()
        val clearLabel = context.getString(R.string.clear_due_date)

        composeRule.setContent {
            DenaroTheme {
                DebtEditorScreen(
                    repository = repository,
                    debtId = debtId,
                    onBack = {},
                    onFinished = { completions.incrementAndGet() },
                    onDeleted = {},
                    onMessage = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Alex").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(clearLabel).performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithContentDescription(clearLabel)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil { completions.get() == 1 }
        assertNull(database.debtDao().getById(debtId)?.dueDate)
    }

    @Test
    fun archivedCategoryRemainsVisibleAndAllowsUnrelatedTransactionEdit() = runBlocking {
        val accountId = repository.createAccount(
            AccountInput("Cash", null, 0, "EUR"),
        )
        val categoryId = repository.createCategory(
            CategoryInput(TransactionType.EXPENSE, null, "Food", "utensils", 1),
        )
        val transactionId = repository.createTransaction(
            TransactionInput(
                accountId = accountId,
                amountMinor = 1_000,
                type = TransactionType.EXPENSE,
                occurredAt = 1,
                description = "Lunch",
                categoryId = categoryId,
            ),
        )
        repository.archiveCategory(categoryId)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completions = AtomicInteger()

        composeRule.setContent {
            DenaroTheme {
                ActivityEditorScreen(
                    repository = repository,
                    route = ActivityEditorRoute(
                        kind = ActivityKind.EXPENSE,
                        id = transactionId,
                    ),
                    onFinished = { completions.incrementAndGet() },
                    onBack = {},
                    onMessage = {},
                )
            }
        }

        val archivedLabel = "Food (${context.getString(R.string.archived)})"
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(archivedLabel).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(archivedLabel).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil { completions.get() == 1 }
        assertEquals(categoryId, repository.getTransaction(transactionId)?.categoryId)
    }

    @Test
    fun newlyCreatedCategoryWinsAfterExistingTransactionLoads() = runBlocking {
        val accountId = repository.createAccount(
            AccountInput("Cash", null, 0, "EUR"),
        )
        val previousCategoryId = repository.createCategory(
            CategoryInput(TransactionType.EXPENSE, null, "Food", "utensils", 1),
        )
        val createdCategoryId = repository.createCategory(
            CategoryInput(TransactionType.EXPENSE, null, "Shopping", "shopping_bag", 2),
        )
        val transactionId = repository.createTransaction(
            TransactionInput(
                accountId = accountId,
                amountMinor = 1_000,
                type = TransactionType.EXPENSE,
                occurredAt = 1,
                description = "Lunch",
                categoryId = previousCategoryId,
            ),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completions = AtomicInteger()
        val consumptions = AtomicInteger()

        composeRule.setContent {
            DenaroTheme {
                ActivityEditorScreen(
                    repository = repository,
                    route = ActivityEditorRoute(
                        kind = ActivityKind.EXPENSE,
                        id = transactionId,
                    ),
                    onFinished = { completions.incrementAndGet() },
                    onBack = {},
                    onMessage = {},
                    createdCategoryId = createdCategoryId,
                    onCreatedCategoryConsumed = { consumptions.incrementAndGet() },
                )
            }
        }

        composeRule.waitUntil { consumptions.get() == 1 }
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()
        composeRule.waitUntil { completions.get() == 1 }

        assertEquals(createdCategoryId, repository.getTransaction(transactionId)?.categoryId)
    }

    @Test
    fun newCategoryCanCreateAndReturnANewParentAndChild() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var completedCategoryId: String? = null
        composeRule.setContent {
            DenaroTheme {
                CategoryEditorScreen(
                    repository = repository,
                    categoryId = null,
                    type = TransactionType.EXPENSE,
                    initialParentId = null,
                    onBack = {},
                    onFinished = { completedCategoryId = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.name))
            .performTextInput("Groceries")
        openNewParentDialog()
        composeRule.onNodeWithText(context.getString(R.string.parent_category_name))
            .performTextInput("Food")
        composeRule.onNodeWithText(context.getString(R.string.use_parent)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil { completedCategoryId != null }
        val child = runBlocking { repository.getCategory(requireNotNull(completedCategoryId)) }
        val parent = runBlocking { repository.getCategory(requireNotNull(child?.parentId)) }
        assertEquals("Groceries", child?.name)
        assertEquals("Food", parent?.name)
        assertEquals(parent?.colorIndex, child?.colorIndex)
    }

    @Test
    fun backingOutAfterStagingParentPersistsNothing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backs = AtomicInteger()
        composeRule.setContent {
            DenaroTheme {
                CategoryEditorScreen(
                    repository = repository,
                    categoryId = null,
                    type = TransactionType.EXPENSE,
                    initialParentId = null,
                    onBack = { backs.incrementAndGet() },
                    onFinished = {},
                )
            }
        }

        openNewParentDialog()
        composeRule.onNodeWithText(context.getString(R.string.parent_category_name))
            .performTextInput("Food")
        composeRule.onNodeWithText(context.getString(R.string.use_parent)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()

        composeRule.waitUntil { backs.get() == 1 }
        assertEquals(0, runBlocking { database.categoryDao().count() })
    }

    @Test
    fun stagedParentCanBeReopenedAndEdited() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var completedCategoryId: String? = null
        composeRule.setContent {
            DenaroTheme {
                CategoryEditorScreen(
                    repository = repository,
                    categoryId = null,
                    type = TransactionType.INCOME,
                    initialParentId = null,
                    onBack = {},
                    onFinished = { completedCategoryId = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.name)).performTextInput("Salary")
        openNewParentDialog()
        composeRule.onNodeWithText(context.getString(R.string.parent_category_name))
            .performTextInput("Work")
        composeRule.onNodeWithText(context.getString(R.string.use_parent)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.parent_category)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.edit_new_parent)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.parent_category_name))
            .performTextReplacement("Employment")
        composeRule.onNodeWithText(context.getString(R.string.use_parent)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil { completedCategoryId != null }
        val child = runBlocking { repository.getCategory(requireNotNull(completedCategoryId)) }
        val parent = runBlocking { repository.getCategory(requireNotNull(child?.parentId)) }
        assertEquals("Employment", parent?.name)
    }

    @Test
    fun existingParentAndNoneBothReplaceAStagedParent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val existingParentId = repository.createCategory(
            CategoryInput(TransactionType.EXPENSE, null, "Existing", "shapes", 2),
        )
        var completedCategoryId: String? = null
        composeRule.setContent {
            DenaroTheme {
                CategoryEditorScreen(
                    repository = repository,
                    categoryId = null,
                    type = TransactionType.EXPENSE,
                    initialParentId = null,
                    onBack = {},
                    onFinished = { completedCategoryId = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.name)).performTextInput("Child")
        openNewParentDialog()
        composeRule.onNodeWithText(context.getString(R.string.parent_category_name))
            .performTextInput("Discarded one")
        composeRule.onNodeWithText(context.getString(R.string.use_parent)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.parent_category)).performClick()
        composeRule.onNodeWithText("Existing").performClick()

        composeRule.onNodeWithText("Existing").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.parent_category)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.create_parent_category))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.parent_category_name))
            .performTextInput("Discarded two")
        composeRule.onNodeWithText(context.getString(R.string.use_parent)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.parent_category)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.none)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil { completedCategoryId != null }
        val child = repository.getCategory(requireNotNull(completedCategoryId))
        assertNull(child?.parentId)
        assertEquals(2, database.categoryDao().count())
        assertEquals("Existing", repository.getCategory(existingParentId)?.name)
    }

    @Test
    fun existingCategoryCannotCreateAParent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val categoryId = repository.createCategory(
            CategoryInput(TransactionType.EXPENSE, null, "Food", "utensils", 1),
        )
        composeRule.setContent {
            DenaroTheme {
                CategoryEditorScreen(
                    repository = repository,
                    categoryId = categoryId,
                    type = TransactionType.EXPENSE,
                    initialParentId = null,
                    onBack = {},
                    onFinished = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Food").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.parent_category)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.create_parent_category))
            .assertDoesNotExist()
    }

    private fun openNewParentDialog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.parent_category)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.create_parent_category))
            .performClick()
    }
}
