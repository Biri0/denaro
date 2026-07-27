package it.rfmariano.denaro.ui

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.FinanceRepository
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

private enum class TopLevelDestination(
    val label: Int,
    val icon: Int,
) {
    HOME(R.string.home, LucideR.drawable.lucide_ic_house),
    ACCOUNTS(R.string.accounts, LucideR.drawable.lucide_ic_wallet),
    ACTIVITY(R.string.activity, LucideR.drawable.lucide_ic_list),
}

@Composable
fun DenaroApp(repository: FinanceRepository) {
    val homeBackStack = rememberNavBackStack(HomeRoute)
    val accountsBackStack = rememberNavBackStack(AccountsRoute)
    val activityBackStack = rememberNavBackStack(ActivityRoute)
    var currentDestination by rememberSaveable {
        mutableStateOf(TopLevelDestination.HOME)
    }
    var amountsVisible by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val recurrenceFailure = stringResource(R.string.recurrence_update_failed)

    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { HomeViewModel(repository) },
    )
    val accountsViewModel: AccountsViewModel = viewModel(
        factory = viewModelFactory { AccountsViewModel(repository) },
    )
    val activityViewModel: ActivityViewModel = viewModel(
        factory = viewModelFactory { ActivityViewModel(repository) },
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
        val backStack = when (currentDestination) {
            TopLevelDestination.HOME -> homeBackStack
            TopLevelDestination.ACCOUNTS -> accountsBackStack
            TopLevelDestination.ACTIVITY -> activityBackStack
        }
        SnackbarHost(hostState = snackbarHostState)
        NavDisplay(
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
                        amountsVisible = amountsVisible,
                        onToggleAmounts = { amountsVisible = !amountsVisible },
                        onAccountClick = { homeBackStack.add(AccountDetailRoute(it)) },
                        onAddAccount = { homeBackStack.add(AccountEditorRoute()) },
                        onSeeAllActivity = {
                            currentDestination = TopLevelDestination.ACTIVITY
                        },
                        onActivityClick = { item ->
                            homeBackStack.add(item.editorRoute())
                        },
                    )
                }
                entry<AccountsRoute> {
                    AccountsRouteContent(
                        viewModel = accountsViewModel,
                        amountsVisible = amountsVisible,
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
                    ActivityRouteContent(
                        viewModel = activityViewModel,
                        amountsVisible = amountsVisible,
                        onAddActivity = {
                            activityBackStack.add(ActivityEditorRoute())
                        },
                        onActivityClick = { item ->
                            activityBackStack.add(item.editorRoute())
                        },
                    )
                }
                entry<AccountDetailRoute> { route ->
                    val detailViewModel: AccountDetailViewModel = viewModel(
                        key = "account-${route.accountId}",
                        factory = viewModelFactory {
                            AccountDetailViewModel(repository, route.accountId)
                        },
                    )
                    AccountDetailRouteContent(
                        viewModel = detailViewModel,
                        amountsVisible = amountsVisible,
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
                        amountsVisible = amountsVisible,
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
                    )
                }
            },
        )
    }
}

private fun ActivityItem.editorRoute(): ActivityEditorRoute =
    ActivityEditorRoute(kind = kind, id = id)
