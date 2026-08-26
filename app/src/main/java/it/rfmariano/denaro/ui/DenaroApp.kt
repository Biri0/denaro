package it.rfmariano.denaro.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.paging.compose.collectAsLazyPagingItems
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.ActivityFilter
import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.CurrencyCatalog
import it.rfmariano.denaro.data.finance.FinanceSession
import it.rfmariano.denaro.data.finance.FinanceSessionProvider
import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository
import it.rfmariano.denaro.data.security.DeviceAuthenticator
import it.rfmariano.denaro.data.security.ProcessUnlockSession
import it.rfmariano.denaro.data.security.SecurityPreferencesRepository
import it.rfmariano.denaro.quickentry.QuickEntryAction
import it.rfmariano.denaro.quickentry.QuickEntryRequest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.composables.icons.lucide.R as LucideR

@Serializable
private data class HomeRoute(val sessionId: Long) : NavKey

@Serializable
private data class AccountsRoute(val sessionId: Long) : NavKey

@Serializable
private data class ActivityRoute(val sessionId: Long) : NavKey

@Serializable
private data object SettingsRoute : NavKey

@Serializable
private data object CategoriesRoute : NavKey

@Serializable
private data object CounterpartiesRoute : NavKey

@Serializable
private data class CategoryEditorRoute(
    val categoryId: String? = null,
    val type: String,
    val parentId: String? = null,
    val selectForActivityEditor: Boolean = false,
) : NavKey

@Serializable
private data class AccountDetailRoute(val accountId: String) : NavKey

@Serializable
private data class BalanceAdjustmentDetailRoute(val adjustmentId: String) : NavKey

@Serializable
private data object ArchivedAccountsRoute : NavKey

@Serializable
data class AccountEditorRoute(val accountId: String? = null) : NavKey

@Serializable
data class ActivityEditorRoute(
    val kind: ActivityKind = ActivityKind.EXPENSE,
    val id: String? = null,
    val recurringRuleId: String? = null,
    val scheduled: Boolean = false,
    val accountId: String? = null,
    val entryId: Long? = null,
) : NavKey

@Serializable
private data class DebtDetailRoute(val debtId: String) : NavKey

@Serializable
private data class DebtEditorRoute(
    val debtId: String? = null,
    val initialDirection: DebtDirection = DebtDirection.BORROWED,
    val entryId: Long? = null,
) : NavKey

@Serializable
private data class DebtRepaymentEditorRoute(val debtId: String, val repaymentId: String? = null) :
    NavKey

private fun QuickEntryRequest.toEditorRoute(): NavKey = when (action) {
    QuickEntryAction.INCOME ->
        ActivityEditorRoute(kind = ActivityKind.INCOME, entryId = id)

    QuickEntryAction.EXPENSE ->
        ActivityEditorRoute(kind = ActivityKind.EXPENSE, entryId = id)

    QuickEntryAction.TRANSFER ->
        ActivityEditorRoute(kind = ActivityKind.TRANSFER, entryId = id)

    QuickEntryAction.BORROW ->
        DebtEditorRoute(initialDirection = DebtDirection.BORROWED, entryId = id)

    QuickEntryAction.LEND ->
        DebtEditorRoute(initialDirection = DebtDirection.LENT, entryId = id)
}

private enum class TopLevelDestination(
    val label: Int,
    val icon: Int,
) {
    HOME(R.string.home, LucideR.drawable.lucide_ic_house),
    ACCOUNTS(R.string.accounts, LucideR.drawable.lucide_ic_wallet),
    ACTIVITY(R.string.activity, LucideR.drawable.lucide_ic_list),
}

data class DenaroAppState(
    val session: FinanceSession,
    val homeViewModel: HomeViewModel,
    val accountsViewModel: AccountsViewModel,
    val activityViewModel: ActivityViewModel,
    val debtsViewModel: DebtsViewModel,
)

@Composable
fun rememberDenaroAppState(
    session: FinanceSession,
): DenaroAppState {
    val repository = session.repository
    val appLocale = LocalConfiguration.current.locales[0]
    val homeViewModel: HomeViewModel = viewModel(
        key = "home-${session.id}",
        factory = viewModelFactory {
            HomeViewModel(
                repository,
                session.initialDashboardMonth,
                appLocale,
            )
        },
    )
    val accountsViewModel: AccountsViewModel = viewModel(
        key = "accounts-${session.id}",
        factory = viewModelFactory { AccountsViewModel(repository) },
    )
    val activityViewModel: ActivityViewModel = viewModel(
        key = "activity-${session.id}",
        factory = viewModelFactory { ActivityViewModel(repository) },
    )
    val debtsViewModel: DebtsViewModel = viewModel(
        key = "debts-${session.id}",
        factory = viewModelFactory { DebtsViewModel(repository) },
    )
    return remember(
        session,
        homeViewModel,
        accountsViewModel,
        activityViewModel,
        debtsViewModel,
    ) {
        DenaroAppState(
            session,
            homeViewModel,
            accountsViewModel,
            activityViewModel,
            debtsViewModel,
        )
    }
}

@Composable
fun DenaroAppPreloader(state: DenaroAppState) {
    val homeState by state.homeViewModel.uiState.collectAsStateWithLifecycle()
    if (shouldPreloadSecondaryTopLevel(homeState)) {
        SecondaryTopLevelPreloader(state)
    }
}

internal fun shouldPreloadSecondaryTopLevel(homeState: HomeUiState): Boolean =
    isHomeFullyDrawn(homeState)

@Composable
private fun SecondaryTopLevelPreloader(state: DenaroAppState) {
    state.accountsViewModel.uiState.collectAsStateWithLifecycle()
    state.debtsViewModel.uiState.collectAsStateWithLifecycle()
    state.activityViewModel.accounts.collectAsStateWithLifecycle()
    state.activityViewModel.categories.collectAsStateWithLifecycle()
    state.activityViewModel.activity.collectAsLazyPagingItems()
}

@Composable
fun DenaroApp(
    state: DenaroAppState,
    financeSessionProvider: FinanceSessionProvider,
    preferencesRepository: AppPreferencesRepository,
    securityPreferencesRepository: SecurityPreferencesRepository,
    processUnlockSession: ProcessUnlockSession,
    authenticator: DeviceAuthenticator,
    quickEntryRequest: QuickEntryRequest? = null,
    onQuickEntryConsumed: (Long) -> Unit = {},
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val settingsScrollState = rememberScrollState()

    key(state.session.id) {
        SessionDenaroApp(
            state = state,
            financeSessionProvider = financeSessionProvider,
            preferencesRepository = preferencesRepository,
            securityPreferencesRepository = securityPreferencesRepository,
            processUnlockSession = processUnlockSession,
            authenticator = authenticator,
            quickEntryRequest = quickEntryRequest,
            onQuickEntryConsumed = onQuickEntryConsumed,
            initialSettingsOpen = settingsOpen,
            onSettingsOpenChanged = { settingsOpen = it },
            settingsScrollState = settingsScrollState,
        )
    }
}

@Composable
private fun SessionDenaroApp(
    state: DenaroAppState,
    financeSessionProvider: FinanceSessionProvider,
    preferencesRepository: AppPreferencesRepository,
    securityPreferencesRepository: SecurityPreferencesRepository,
    processUnlockSession: ProcessUnlockSession,
    authenticator: DeviceAuthenticator,
    quickEntryRequest: QuickEntryRequest?,
    onQuickEntryConsumed: (Long) -> Unit,
    initialSettingsOpen: Boolean,
    onSettingsOpenChanged: (Boolean) -> Unit,
    settingsScrollState: ScrollState,
) {
    val session = state.session
    val repository = session.repository
    val preferences by preferencesRepository.state.collectAsStateWithLifecycle()
    val initialQuickEntry = remember { quickEntryRequest }
    val initialQuickEntryRoute = remember(initialQuickEntry) {
        initialQuickEntry?.toEditorRoute()
    }
    val initialHomeRoutes = remember {
        if (initialSettingsOpen) {
            arrayOf(HomeRoute(session.id), SettingsRoute)
        } else {
            arrayOf(HomeRoute(session.id))
        }
    }
    val homeBackStack = rememberNavBackStack(*initialHomeRoutes)
    val accountsBackStack = rememberNavBackStack(AccountsRoute(session.id))
    val activityBackStack = if (initialQuickEntryRoute == null) {
        rememberNavBackStack(ActivityRoute(session.id))
    } else {
        rememberNavBackStack(ActivityRoute(session.id), initialQuickEntryRoute)
    }
    var currentDestination by rememberSaveable(initialQuickEntry?.id) {
        mutableStateOf(
            if (initialQuickEntry != null) {
                TopLevelDestination.ACTIVITY
            } else {
                TopLevelDestination.HOME
            },
        )
    }
    var handledQuickEntryId by remember {
        mutableStateOf(initialQuickEntry?.id)
    }
    var pendingActivityCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val recurrenceFailure = stringResource(R.string.recurrence_update_failed)

    LaunchedEffect(quickEntryRequest?.id) {
        val request = quickEntryRequest ?: return@LaunchedEffect
        if (handledQuickEntryId == request.id) {
            onQuickEntryConsumed(request.id)
            return@LaunchedEffect
        }
        currentDestination = TopLevelDestination.ACTIVITY
        activityBackStack.add(request.toEditorRoute())
        handledQuickEntryId = request.id
        onQuickEntryConsumed(request.id)
    }

    val homeViewModel = state.homeViewModel
    val accountsViewModel = state.accountsViewModel
    val activityViewModel = state.activityViewModel
    val debtsViewModel = state.debtsViewModel

    fun processRecurrences() {
        scope.launch {
            runCatching { repository.processDueRecurrences() }
                .onSuccess {
                    financeSessionProvider.clearRecurrenceStartupFailure(session.id)
                }
                .onFailure { snackbarHostState.showSnackbar(recurrenceFailure) }
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    var wasPaused by remember(repository) { mutableStateOf(false) }
    RecurrenceStartupFailureEffect(
        failurePending = session.recurrenceStartupFailed,
        onFailureConsumed = {
            financeSessionProvider.clearRecurrenceStartupFailure(session.id)
        },
        showFailure = { snackbarHostState.showSnackbar(recurrenceFailure) },
    )
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        wasPaused = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (wasPaused) {
            wasPaused = false
            processRecurrences()
        }
    }

    val backStack = when (currentDestination) {
        TopLevelDestination.HOME -> homeBackStack
        TopLevelDestination.ACCOUNTS -> accountsBackStack
        TopLevelDestination.ACTIVITY -> activityBackStack
    }
    val onBack: () -> Unit = {
        if (backStack.size > 1) {
            if (backStack.lastOrNull() is SettingsRoute) {
                onSettingsOpenChanged(false)
            }
            backStack.removeLastOrNull()
        } else if (currentDestination != TopLevelDestination.HOME) {
            currentDestination = TopLevelDestination.HOME
        }
    }
    val navEntryDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val decoratedEntries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(navEntryDecorator),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeRouteContent(
                    viewModel = homeViewModel,
                    amountsVisible = preferences.amountsVisible,
                    onToggleAmounts = {
                        preferencesRepository.setAmountsVisible(!preferences.amountsVisible)
                    },
                    onAddAccount = { homeBackStack.add(AccountEditorRoute()) },
                    onSettings = {
                        onSettingsOpenChanged(true)
                        homeBackStack.add(SettingsRoute)
                    },
                    onCategoryClick = { kind, categoryId, fromDate, toDate, currency, accountId ->
                        activityViewModel.applyFilter(
                            ActivityFilter(
                                kind,
                                accountId,
                                currency,
                                categoryId,
                                fromDate,
                                toDate
                            ),
                        )
                        currentDestination = TopLevelDestination.ACTIVITY
                    },
                )
            }
            entry<SettingsRoute> {
                SettingsScreen(
                    financeSessionProvider = financeSessionProvider,
                    preferencesRepository = preferencesRepository,
                    securityPreferencesRepository = securityPreferencesRepository,
                    processUnlockSession = processUnlockSession,
                    authenticator = authenticator,
                    scrollState = settingsScrollState,
                    onBack = onBack,
                    onCategories = { homeBackStack.add(CategoriesRoute) },
                    onCounterparties = { homeBackStack.add(CounterpartiesRoute) },
                )
            }
            entry<CounterpartiesRoute> {
                CounterpartiesScreen(repository) { homeBackStack.removeLastOrNull() }
            }
            entry<CategoriesRoute> {
                CategoryManagementScreen(
                    repository = repository,
                    onBack = { homeBackStack.removeLastOrNull() },
                    onEdit = { id, type, parentId ->
                        homeBackStack.add(CategoryEditorRoute(id, type.name, parentId))
                    },
                )
            }
            entry<CategoryEditorRoute> { route ->
                CategoryEditorScreen(
                    repository = repository,
                    categoryId = route.categoryId,
                    type = TransactionType.valueOf(route.type),
                    initialParentId = route.parentId,
                    onBack = { backStack.removeLastOrNull() },
                    onFinished = { categoryId ->
                        if (route.selectForActivityEditor) {
                            pendingActivityCategoryId = categoryId
                        }
                        backStack.removeLastOrNull()
                    },
                )
            }
            entry<AccountsRoute> {
                AccountsRouteContent(
                    viewModel = accountsViewModel,
                    amountsVisible = preferences.amountsVisible,
                    onAccountClick = {
                        accountsBackStack.add(AccountDetailRoute(it))
                    },
                    onAddAccount = {
                        accountsBackStack.add(AccountEditorRoute())
                    },
                    onArchivedAccounts = {
                        accountsBackStack.add(ArchivedAccountsRoute)
                    },
                )
            }
            entry<ActivityRoute> {
                val debtsState by debtsViewModel.uiState.collectAsStateWithLifecycle()
                ActivityRouteContent(
                    viewModel = activityViewModel,
                    amountsVisible = preferences.amountsVisible,
                    onAddActivity = {
                        activityBackStack.add(ActivityEditorRoute())
                    },
                    onActivityClick = { item ->
                        if (item.kind == ActivityKind.DEBT && item.debtId != null) {
                            activityBackStack.add(DebtDetailRoute(item.debtId))
                        } else if (item.kind == ActivityKind.ADJUSTMENT) {
                            activityBackStack.add(BalanceAdjustmentDetailRoute(item.id))
                        } else {
                            activityBackStack.add(item.editorRoute())
                        }
                    },
                    debts = debtsState.debts,
                    onAddDebt = { activityBackStack.add(DebtEditorRoute()) },
                    onDebtClick = { activityBackStack.add(DebtDetailRoute(it)) },
                )
            }
            entry<DebtDetailRoute> { route ->
                val detail: DebtDetailViewModel = viewModel(
                    key = "debt-${session.id}-${route.debtId}",
                    factory = viewModelFactory {
                        DebtDetailViewModel(
                            repository,
                            route.debtId
                        )
                    },
                )
                val state by detail.uiState.collectAsStateWithLifecycle()
                val accounts by remember(repository) { repository.observeAllAccounts() }.collectAsStateWithLifecycle(
                    emptyList()
                )
                DebtDetailScreen(
                    state = state,
                    accounts = accounts,
                    amountsVisible = preferences.amountsVisible,
                    onBack = { backStack.removeLastOrNull() },
                    onEdit = { backStack.add(DebtEditorRoute(it)) },
                    onAddRepayment = { backStack.add(DebtRepaymentEditorRoute(it)) },
                    onEditRepayment = { debtId, repaymentId ->
                        backStack.add(
                            DebtRepaymentEditorRoute(debtId, repaymentId)
                        )
                    },
                )
            }
            entry<DebtEditorRoute> { route ->
                DebtEditorScreen(
                    repository,
                    route.debtId,
                    initialDirection = route.initialDirection,
                    onBack = { backStack.removeLastOrNull() },
                    onFinished = { backStack.removeLastOrNull() },
                    onDeleted = {
                        backStack.removeLastOrNull()
                        backStack.removeLastOrNull()
                    },
                    onMessage = ::showMessage,
                )
            }
            entry<DebtRepaymentEditorRoute> { route ->
                DebtRepaymentEditorScreen(
                    repository, route.debtId, route.repaymentId,
                    onBack = { backStack.removeLastOrNull() },
                    onFinished = { backStack.removeLastOrNull() },
                    onMessage = ::showMessage,
                )
            }
            entry<AccountDetailRoute> { route ->
                val detailViewModel: AccountDetailViewModel = viewModel(
                    key = "account-${session.id}-${route.accountId}",
                    factory = viewModelFactory {
                        AccountDetailViewModel(repository, route.accountId)
                    },
                )
                AccountDetailRouteContent(
                    viewModel = detailViewModel,
                    amountsVisible = preferences.amountsVisible,
                    onBack = { backStack.removeLastOrNull() },
                    onEdit = { backStack.add(AccountEditorRoute(it)) },
                    onEditSchedule = {
                        backStack.add(ActivityEditorRoute(recurringRuleId = it))
                    },
                    onMessage = ::showMessage,
                )
            }
            entry<BalanceAdjustmentDetailRoute> { route ->
                val detailViewModel: BalanceAdjustmentDetailViewModel = viewModel(
                    key = "adjustment-${session.id}-${route.adjustmentId}",
                    factory = viewModelFactory {
                        BalanceAdjustmentDetailViewModel(repository, route.adjustmentId)
                    },
                )
                BalanceAdjustmentDetailRouteContent(
                    viewModel = detailViewModel,
                    amountsVisible = preferences.amountsVisible,
                    onBack = { backStack.removeLastOrNull() },
                    onDeleted = { backStack.removeLastOrNull() },
                    onMessage = ::showMessage,
                )
            }
            entry<ArchivedAccountsRoute> {
                val state by accountsViewModel.uiState.collectAsStateWithLifecycle()
                ArchivedAccountsRouteContent(
                    accounts = state.archived,
                    isLoading = state.isLoading,
                    amountsVisible = preferences.amountsVisible,
                    onBack = { backStack.removeLastOrNull() },
                    onRestore = { accountId ->
                        scope.launch {
                            runCatching { repository.restoreAccount(accountId) }
                                .onFailure {
                                    snackbarHostState.showSnackbar(
                                        it.message ?: recurrenceFailure,
                                    )
                                }
                        }
                    },
                )
            }
            entry<AccountEditorRoute> { route ->
                val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
                val accounts = homeUiState.accounts
                val usedCurrencies = accounts.map { it.currency }
                val appLocale = LocalConfiguration.current.locales[0]
                val includeAll = preferences.showAllCurrencies
                val defaultCurrency = remember(usedCurrencies, appLocale, includeAll) {
                    CurrencyCatalog.automaticDefault(
                        usedCurrencies,
                        appLocale,
                        includeAll = includeAll,
                    )
                }
                AccountEditorScreen(
                    repository = repository,
                    defaultCurrency = defaultCurrency,
                    accountId = route.accountId,
                    usedCurrencies = usedCurrencies,
                    includeAll = includeAll,
                    onFinished = { backStack.removeLastOrNull() },
                    onBack = { backStack.removeLastOrNull() },
                    onMessage = ::showMessage,
                )
            }
            entry<ActivityEditorRoute> { route ->
                ActivityEditorScreen(
                    repository = repository,
                    route = route,
                    onFinished = { backStack.removeLastOrNull() },
                    onBack = { backStack.removeLastOrNull() },
                    onMessage = ::showMessage,
                    onEditSchedule = {
                        backStack.add(ActivityEditorRoute(recurringRuleId = it))
                    },
                    createdCategoryId = pendingActivityCategoryId,
                    onCreatedCategoryConsumed = { pendingActivityCategoryId = null },
                    onAddCategory = { type ->
                        backStack.add(
                            CategoryEditorRoute(
                                type = type.name,
                                selectForActivityEditor = true,
                            ),
                        )
                    },
                )
            }
        },
    )
    val appContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                entries = decoratedEntries,
                modifier = Modifier.fillMaxSize(),
                onBack = onBack,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }
    val fullScreenSettings = when (backStack.lastOrNull()) {
        is SettingsRoute, is CategoriesRoute, is CategoryEditorRoute, is CounterpartiesRoute -> true
        else -> false
    }
    if (quickEntryRequest != null && handledQuickEntryId != quickEntryRequest.id) {
        Box(Modifier.fillMaxSize())
        return
    }
    val navigationSuiteState = rememberNavigationSuiteScaffoldState(
        initialValue = if (fullScreenSettings) {
            NavigationSuiteScaffoldValue.Hidden
        } else {
            NavigationSuiteScaffoldValue.Visible
        },
    )
    LaunchedEffect(fullScreenSettings) {
        if (fullScreenSettings) {
            navigationSuiteState.hide()
        } else {
            navigationSuiteState.show()
        }
    }
    NavigationSuiteScaffold(
        state = navigationSuiteState,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination == destination,
                    onClick = { currentDestination = destination },
                    icon = {
                        Icon(
                            painter = painterResource(destination.icon),
                            contentDescription = stringResource(destination.label),
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        appContent()
    }
}

@Composable
internal fun RecurrenceStartupFailureEffect(
    failurePending: Boolean,
    onFailureConsumed: () -> Unit,
    showFailure: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(failurePending) {
        if (failurePending) {
            onFailureConsumed()
            scope.launch { showFailure() }
        }
    }
}

private fun ActivityItem.editorRoute(): ActivityEditorRoute =
    ActivityEditorRoute(kind = kind, id = id)
