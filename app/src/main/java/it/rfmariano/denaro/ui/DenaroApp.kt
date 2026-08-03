package it.rfmariano.denaro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.ActivityFilter
import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.FinanceSession
import it.rfmariano.denaro.data.finance.FinanceSessionProvider
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.composables.icons.lucide.R as LucideR

@Serializable
private data object HomeRoute : NavKey

@Serializable
private data object AccountsRoute : NavKey

@Serializable
private data object ActivityRoute : NavKey

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
) : NavKey

@Serializable
private data class DebtDetailRoute(val debtId: String) : NavKey

@Serializable
private data class DebtEditorRoute(val debtId: String? = null) : NavKey

@Serializable
private data class DebtRepaymentEditorRoute(val debtId: String, val repaymentId: String? = null) :
    NavKey

private enum class TopLevelDestination(
    val label: Int,
    val icon: Int,
) {
    HOME(R.string.home, LucideR.drawable.lucide_ic_house),
    ACCOUNTS(R.string.accounts, LucideR.drawable.lucide_ic_wallet),
    ACTIVITY(R.string.activity, LucideR.drawable.lucide_ic_list),
}

@Composable
fun DenaroApp(
    session: FinanceSession,
    financeSessionProvider: FinanceSessionProvider,
    preferencesRepository: AppPreferencesRepository,
) {
    val repository = session.repository
    val preferences by preferencesRepository.state.collectAsStateWithLifecycle()
    val homeBackStack = rememberNavBackStack(HomeRoute)
    val accountsBackStack = rememberNavBackStack(AccountsRoute)
    val activityBackStack = rememberNavBackStack(ActivityRoute)
    var currentDestination by rememberSaveable {
        mutableStateOf(TopLevelDestination.HOME)
    }
    var pendingActivityCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val recurrenceFailure = stringResource(R.string.recurrence_update_failed)

    val homeViewModel: HomeViewModel = viewModel(
        key = "home-${session.id}",
        factory = viewModelFactory {
            HomeViewModel(
                repository,
                preferences.defaultCurrency,
                session.initialDashboardMonth,
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

    fun processRecurrences() {
        scope.launch {
            runCatching { repository.processDueRecurrences() }
                .onFailure { snackbarHostState.showSnackbar(recurrenceFailure) }
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(repository) {
        processRecurrences()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        processRecurrences()
    }

    val backStack = when (currentDestination) {
        TopLevelDestination.HOME -> homeBackStack
        TopLevelDestination.ACCOUNTS -> accountsBackStack
        TopLevelDestination.ACTIVITY -> activityBackStack
    }
    val appContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                modifier = Modifier.fillMaxSize(),
                backStack = backStack,
                onBack = {
                    if (backStack.size > 1) {
                        backStack.removeLastOrNull()
                    } else if (currentDestination != TopLevelDestination.HOME) {
                        currentDestination = TopLevelDestination.HOME
                    }
                },
                entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeRouteContent(
                        viewModel = homeViewModel,
                        amountsVisible = preferences.amountsVisible,
                        onToggleAmounts = {
                            preferencesRepository.setAmountsVisible(!preferences.amountsVisible)
                        },
                        onAddAccount = { homeBackStack.add(AccountEditorRoute()) },
                        onSettings = { homeBackStack.add(SettingsRoute) },
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
                            onBack = { homeBackStack.removeLastOrNull() },
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
                            repository, route.debtId,
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
                    AccountEditorScreen(
                        repository = repository,
                        defaultCurrency = preferences.defaultCurrency,
                        accountId = route.accountId,
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
    if (fullScreenSettings) {
        appContent()
    } else {
        NavigationSuiteScaffold(
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
}

private fun ActivityItem.editorRoute(): ActivityEditorRoute =
    ActivityEditorRoute(kind = kind, id = id)
