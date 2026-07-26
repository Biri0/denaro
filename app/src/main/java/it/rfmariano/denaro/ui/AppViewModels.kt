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

data class HomeUiState(
    val accounts: List<AccountSummary> = emptyList(),
    val totalsByCurrency: Map<String, Long> = emptyMap(),
    val recentActivity: List<ActivityItem> = emptyList(),
)

class HomeViewModel(repository: FinanceRepository) : ViewModel() {
    val uiState = combine(
        repository.observeActiveAccounts(),
        repository.observeRecentActivity(),
    ) { accounts, activity ->
        HomeUiState(
            accounts = accounts,
            totalsByCurrency = accounts
                .groupBy(AccountSummary::currency)
                .mapValues { (_, values) -> values.sumOf(AccountSummary::balanceMinor) }
                .toSortedMap(),
            recentActivity = activity,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(),
    )
}

data class AccountsUiState(
    val active: List<AccountSummary> = emptyList(),
    val archived: List<AccountSummary> = emptyList(),
)

class AccountsViewModel(repository: FinanceRepository) : ViewModel() {
    val uiState = combine(
        repository.observeActiveAccounts(),
        repository.observeArchivedAccounts(),
        ::AccountsUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AccountsUiState(),
    )
}

data class AccountDetailUiState(
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
        ::AccountDetailUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AccountDetailUiState(),
    )
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivityViewModel(private val repository: FinanceRepository) : ViewModel() {
    val selectedKind = MutableStateFlow<ActivityKind?>(null)
    val selectedAccountId = MutableStateFlow<String?>(null)
    val accounts = repository.observeActiveAccounts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val activity: Flow<PagingData<ActivityItem>> = combine(
        selectedKind,
        selectedAccountId,
        ::Pair,
    ).flatMapLatest { (kind, accountId) ->
        repository.activityPager(kind, accountId)
    }.cachedIn(viewModelScope)
}

inline fun <reified T : ViewModel> viewModelFactory(
    crossinline create: () -> T,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create() }
}
