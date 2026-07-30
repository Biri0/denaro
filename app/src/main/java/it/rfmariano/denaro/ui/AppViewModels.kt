package it.rfmariano.denaro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.RecurringRuleSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class HomeUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountSummary> = emptyList(),
    val totalsByCurrency: Map<String, Long> = emptyMap(),
    val recentActivity: List<ActivityItem> = emptyList(),
)

class HomeViewModel(private val repository: FinanceRepository) : ViewModel() {
    private val refreshRequests = MutableStateFlow(0L)

    val uiState = refreshableFlow(refreshRequests, HomeUiState()) {
        combine(
            repository.observeActiveAccounts(),
            repository.observeRecentActivity(),
        ) { accounts, activity ->
            HomeUiState(
                isLoading = false,
                accounts = accounts,
                totalsByCurrency = accounts
                    .groupBy(AccountSummary::currency)
                    .mapValues { (_, values) -> values.sumOf(AccountSummary::balanceMinor) }
                    .toSortedMap(),
                recentActivity = activity,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(),
    )

    fun refresh() {
        refreshRequests.update { it + 1 }
    }
}

data class AccountsUiState(
    val isLoading: Boolean = true,
    val active: List<AccountSummary> = emptyList(),
    val archived: List<AccountSummary> = emptyList(),
)

class AccountsViewModel(private val repository: FinanceRepository) : ViewModel() {
    private val refreshRequests = MutableStateFlow(0L)

    val uiState = refreshableFlow(refreshRequests, AccountsUiState()) {
        combine(
            repository.observeActiveAccounts(),
            repository.observeArchivedAccounts(),
        ) { active, archived ->
            AccountsUiState(
                isLoading = false,
                active = active,
                archived = archived,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AccountsUiState(),
    )

    fun refresh() {
        refreshRequests.update { it + 1 }
    }
}

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val account: AccountSummary? = null,
    val recurringRules: List<RecurringRuleSummary> = emptyList(),
)

class AccountDetailViewModel(
    repository: FinanceRepository,
    accountId: String,
) : ViewModel() {
    val uiState = combine(
        repository.observeAccount(accountId),
        repository.observeRecurringRules(accountId),
    ) { account, recurringRules ->
        AccountDetailUiState(
            isLoading = false,
            account = account,
            recurringRules = recurringRules,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AccountDetailUiState(),
    )
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivityViewModel(private val repository: FinanceRepository) : ViewModel() {
    val selectedKind = MutableStateFlow<ActivityKind?>(null)
    val selectedAccountId = MutableStateFlow<String?>(null)
    private val refreshRequests = MutableStateFlow(0L)
    val accounts = repository.observeActiveAccounts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val activity: Flow<PagingData<ActivityItem>> = combine(
        selectedKind,
        selectedAccountId,
        refreshRequests,
    ) { kind, accountId, _ ->
        kind to accountId
    }.flatMapLatest { (kind, accountId) ->
        repository.activityPager(kind, accountId)
    }.cachedIn(viewModelScope)

    fun refresh() {
        refreshRequests.update { it + 1 }
    }
}

inline fun <reified T : ViewModel> viewModelFactory(
    crossinline create: () -> T,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create() }
}
