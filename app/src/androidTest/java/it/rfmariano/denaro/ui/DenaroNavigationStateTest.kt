package it.rfmariano.denaro.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountInput
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.FinanceSession
import it.rfmariano.denaro.data.finance.FinanceSessionProvider
import it.rfmariano.denaro.data.local.DenaroDatabase
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository
import it.rfmariano.denaro.data.security.DeviceAuthenticationResult
import it.rfmariano.denaro.data.security.DeviceAuthenticator
import it.rfmariano.denaro.data.security.ProcessUnlockSession
import it.rfmariano.denaro.data.security.SecurityPreferencesRepository
import it.rfmariano.denaro.quickentry.QuickEntryAction
import it.rfmariano.denaro.quickentry.QuickEntryRequest
import it.rfmariano.denaro.ui.theme.DenaroTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class DenaroNavigationStateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: DenaroDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setUp() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            database = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
            repository = FinanceRepository(database, clock = { 10 })
            repository.createAccount(AccountInput("Cash", null, 0, "EUR"))
        }
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
    fun creatingIncomeCategoryPreservesAndSubmitsActivityDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setDenaroContent()
        openIncomeActivityEditor()

        composeRule.onNodeWithText(context.getString(R.string.amount))
            .performTextInput("12.34")
        composeRule.onNodeWithText(context.getString(R.string.description))
            .performTextInput("Consulting")
        openCategoryEditor()
        composeRule.onNodeWithText(context.getString(R.string.name))
            .performTextInput("Client work")
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil {
            composeRule.onNodeWithText("Client work").isDisplayed()
        }
        composeRule.onNodeWithText(context.getString(R.string.income)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.amount))
            .assertTextContains("12.34")
        composeRule.onNodeWithText(context.getString(R.string.description))
            .assertTextContains("Consulting")
        composeRule.onNodeWithText("Client work").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        composeRule.waitUntil {
            runBlocking { database.transactionDao().count() == 1 }
        }
        val transaction = runBlocking {
            database.transactionDao().observeAll().first().single()
        }
        val category = runBlocking {
            database.categoryDao().getById(requireNotNull(transaction.categoryId))
        }
        assertEquals(TransactionType.INCOME, transaction.type)
        assertEquals(1_234L, transaction.amountMinor)
        assertEquals("Consulting", transaction.description)
        assertEquals(TransactionType.INCOME, category?.type)
        assertEquals("Client work", category?.name)
    }

    @Test
    fun cancelingCategoryCreationPreservesActivityDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setDenaroContent()
        openIncomeActivityEditor()

        composeRule.onNodeWithText(context.getString(R.string.amount))
            .performTextInput("45.67")
        composeRule.onNodeWithText(context.getString(R.string.description))
            .performTextInput("Refund")
        openCategoryEditor()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.income)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.amount))
            .assertTextContains("45.67")
        composeRule.onNodeWithText(context.getString(R.string.description))
            .assertTextContains("Refund")
        assertEquals(0, runBlocking { database.categoryDao().count() })
    }

    @Test
    fun secondQuickEntryStacksOverAndPreservesExistingDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = FinanceSession(
            id = 1,
            repository = repository,
            isDemo = false,
            initialDashboardMonth = YearMonth.now().toString(),
        )
        val financeSessionProvider = TestFinanceSessionProvider(session)
        val preferencesRepository = AppPreferencesRepository(context)
        val securityPreferencesRepository = SecurityPreferencesRepository(context)
        val processUnlockSession = ProcessUnlockSession(appLockEnabledAtProcessStart = false)
        var request by mutableStateOf<QuickEntryRequest?>(
            QuickEntryRequest(id = 1, action = QuickEntryAction.INCOME),
        )

        composeRule.setContent {
            DenaroTheme {
                DenaroApp(
                    state = rememberDenaroAppState(session, defaultCurrency = "EUR"),
                    financeSessionProvider = financeSessionProvider,
                    preferencesRepository = preferencesRepository,
                    securityPreferencesRepository = securityPreferencesRepository,
                    processUnlockSession = processUnlockSession,
                    authenticator = TestDeviceAuthenticator,
                    quickEntryRequest = request,
                    onQuickEntryConsumed = {},
                )
            }
        }

        composeRule.waitUntil {
            runCatching {
                composeRule.onNodeWithText(context.getString(R.string.income))
                    .fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithText(context.getString(R.string.income)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.amount))
            .performTextInput("12.34")

        composeRule.runOnUiThread {
            request = QuickEntryRequest(id = 2, action = QuickEntryAction.EXPENSE)
        }
        composeRule.waitUntil {
            runCatching {
                composeRule.onNodeWithText(context.getString(R.string.expense)).isDisplayed()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText(context.getString(R.string.expense)).assertIsSelected()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.income)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.amount))
            .assertTextContains("12.34")
    }

    @Test
    fun replacingFinanceSessionFromSettingsUsesFreshNavigationRoots() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val demoDatabase = Room.inMemoryDatabaseBuilder(context, DenaroDatabase::class.java).build()
        try {
            val demoRepository = FinanceRepository(demoDatabase, clock = { 20 })
            demoRepository.createAccount(AccountInput("Demo cash", null, 0, "EUR"))
            val productionSession = FinanceSession(
                id = 1,
                repository = repository,
                isDemo = false,
                initialDashboardMonth = YearMonth.now().toString(),
            )
            val demoSession = FinanceSession(
                id = 2,
                repository = demoRepository,
                isDemo = true,
                initialDashboardMonth = YearMonth.now().toString(),
            )
            val financeSessionProvider = TestFinanceSessionProvider(productionSession)
            val preferencesRepository = AppPreferencesRepository(context)
            val securityPreferencesRepository = SecurityPreferencesRepository(context)
            val processUnlockSession = ProcessUnlockSession(appLockEnabledAtProcessStart = false)
            var activeSession by mutableStateOf(productionSession)

            composeRule.setContent {
                DenaroTheme {
                    val appState = rememberDenaroAppState(activeSession, defaultCurrency = "EUR")
                    key(activeSession.id) {
                        DenaroApp(
                            state = appState,
                            financeSessionProvider = financeSessionProvider,
                            preferencesRepository = preferencesRepository,
                            securityPreferencesRepository = securityPreferencesRepository,
                            processUnlockSession = processUnlockSession,
                            authenticator = TestDeviceAuthenticator,
                        )
                    }
                }
            }

            composeRule.waitUntil(5_000) {
                runCatching {
                    composeRule.onNodeWithContentDescription(context.getString(R.string.settings))
                        .isDisplayed()
                }.getOrDefault(false)
            }
            composeRule.onNodeWithContentDescription(context.getString(R.string.settings))
                .performClick()
            composeRule.waitUntil {
                runCatching {
                    composeRule.onNodeWithText(context.getString(R.string.settings)).isDisplayed()
                }.getOrDefault(false)
            }

            composeRule.runOnUiThread { activeSession = demoSession }
            composeRule.waitUntil(5_000) {
                runCatching {
                    composeRule.onNodeWithContentDescription(context.getString(R.string.settings))
                        .isDisplayed()
                }
                    .getOrDefault(false)
            }
            composeRule.onNodeWithText(context.getString(R.string.accounts)).performClick()
            composeRule.waitUntil(5_000) {
                runCatching { composeRule.onNodeWithText("Demo cash").isDisplayed() }
                    .getOrDefault(false)
            }

            composeRule.runOnUiThread { activeSession = productionSession }
            composeRule.waitUntil(5_000) {
                runCatching {
                    composeRule.onNodeWithContentDescription(context.getString(R.string.settings))
                        .isDisplayed()
                }
                    .getOrDefault(false)
            }
            composeRule.onNodeWithText(context.getString(R.string.accounts)).performClick()
            composeRule.waitUntil(5_000) {
                runCatching { composeRule.onNodeWithText("Cash").isDisplayed() }
                    .getOrDefault(false)
            }
        } finally {
            composeRule.activityRule.scenario.onActivity { activity -> activity.setContent {} }
            composeRule.waitForIdle()
            demoDatabase.close()
        }
    }

    private fun setDenaroContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = FinanceSession(
            id = 1,
            repository = repository,
            isDemo = false,
            initialDashboardMonth = YearMonth.now().toString(),
        )
        val financeSessionProvider = TestFinanceSessionProvider(session)
        val preferencesRepository = AppPreferencesRepository(context)
        val securityPreferencesRepository = SecurityPreferencesRepository(context)
        val processUnlockSession = ProcessUnlockSession(appLockEnabledAtProcessStart = false)

        composeRule.setContent {
            DenaroTheme {
                DenaroApp(
                    state = rememberDenaroAppState(session, defaultCurrency = "EUR"),
                    financeSessionProvider = financeSessionProvider,
                    preferencesRepository = preferencesRepository,
                    securityPreferencesRepository = securityPreferencesRepository,
                    processUnlockSession = processUnlockSession,
                    authenticator = TestDeviceAuthenticator,
                )
            }
        }
    }

    private fun openIncomeActivityEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.activity),
            useUnmergedTree = true,
        )
            .performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.add_activity),
            useUnmergedTree = true,
        )
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.income)).performClick()
        composeRule.waitUntil {
            composeRule.onNodeWithText("Cash").isDisplayed()
        }
    }

    private fun openCategoryEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.category)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.add_category)).performClick()
    }

    private class TestFinanceSessionProvider(
        financeSession: FinanceSession,
    ) : FinanceSessionProvider {
        private val mutableSession = MutableStateFlow<FinanceSession?>(financeSession)

        override val session: StateFlow<FinanceSession?> = mutableSession

        override suspend fun initialize(localeTag: String) = Unit

        override suspend fun setDemoMode(enabled: Boolean, localeTag: String) = Unit

        override fun clearRecurrenceStartupFailure(sessionId: Long) = Unit
    }

    private data object TestDeviceAuthenticator : DeviceAuthenticator {
        override suspend fun authenticate(
            title: String,
            subtitle: String,
        ): DeviceAuthenticationResult = DeviceAuthenticationResult.Success

        override fun openSecuritySettings() = Unit
    }
}
