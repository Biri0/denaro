package it.rfmariano.denaro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.ActivityFilter
import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.BalanceAdjustmentSummary
import it.rfmariano.denaro.data.finance.CurrencyCatalog
import it.rfmariano.denaro.data.finance.DashboardFilter
import it.rfmariano.denaro.data.finance.DashboardSnapshot
import it.rfmariano.denaro.data.finance.DebtRepaymentSummary
import it.rfmariano.denaro.data.finance.DebtSummary
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.RecurringRuleSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.Locale

data class HomeUiState(
    val isLoading: Boolean = true,
    val isDashboardLoading: Boolean = true,
    val accounts: List<AccountSummary> = emptyList(),
    val totalsByCurrency: Map<String, Long> = emptyMap(),
    val dashboard: DashboardSnapshot? = null,
    val selectedCurrency: String = "EUR",
    val selectedAccountId: String? = null,
    val selectedMonth: String = YearMonth.now().toString(),
)

private data class DashboardLoadState(
    val filter: DashboardFilter,
    val snapshot: DashboardSnapshot? = null,
    val isLoading: Boolean = true,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: FinanceRepository,
    initialDashboardMonth: String = YearMonth.now().toString(),
    locale: Locale = Locale.getDefault(),
) : ViewModel() {
    private val refreshRequests = MutableStateFlow(0L)
    private val initialCurrency = CurrencyCatalog.automaticDefault(emptyList(), locale)
    val selectedCurrency = MutableStateFlow(initialCurrency)
    val selectedAccountId = MutableStateFlow<String?>(null)
    val selectedMonth = MutableStateFlow(initialDashboardMonth)

    init {
        viewModelScope.launch {
            val accounts = repository.observeActiveAccounts().first()
            if (selectedCurrency.value == initialCurrency) {
                selectedCurrency.value = CurrencyCatalog.automaticDefault(
                    accounts.map(AccountSummary::currency),
                    locale,
                )
            }
        }
    }

    private val dashboard = combine(
        selectedCurrency,
        selectedAccountId,
        selectedMonth,
        refreshRequests,
    ) { currency, accountId, month, _ ->
        DashboardFilter(currency, accountId, month)
    }.flatMapLatest { filter ->
        repository.observeDashboard(filter)
            .map { DashboardLoadState(filter, it, isLoading = false) }
            .onStart { emit(DashboardLoadState(filter)) }
    }

    val uiState = combine(
        repository.observeActiveAccounts(),
        dashboard,
    ) { accounts, dashboardState ->
        HomeUiState(
            isLoading = false,
            isDashboardLoading = dashboardState.isLoading,
            accounts = accounts,
            totalsByCurrency = accounts
                .groupBy(AccountSummary::currency)
                .mapValues { (_, values) -> values.sumOf(AccountSummary::balanceMinor) }
                .toSortedMap(),
            dashboard = dashboardState.snapshot,
            selectedCurrency = dashboardState.filter.currency,
            selectedAccountId = dashboardState.filter.accountId,
            selectedMonth = dashboardState.filter.selectedMonth,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(),
    )

    fun refresh() {
        refreshRequests.update { it + 1 }
    }

    fun previousMonth() {
        selectedMonth.value = YearMonth.parse(selectedMonth.value).minusMonths(1).toString()
    }

    fun nextMonth() {
        val next = YearMonth.parse(selectedMonth.value).plusMonths(1)
        if (next <= YearMonth.now()) selectedMonth.value = next.toString()
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
    private val repository: FinanceRepository,
    private val accountId: String,
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

    suspend fun setBalance(targetBalanceMinor: Long): Result<Unit> = runCatching {
        repository.createBalanceAdjustment(accountId, targetBalanceMinor)
    }

    suspend fun delete(): Result<Unit> = runCatching {
        repository.deleteAccount(accountId)
    }

    suspend fun archive(): Result<Unit> = runCatching {
        repository.archiveAccount(accountId)
    }
}

data class BalanceAdjustmentDetailUiState(
    val isLoading: Boolean = true,
    val adjustment: BalanceAdjustmentSummary? = null,
)

class BalanceAdjustmentDetailViewModel(
    private val repository: FinanceRepository,
    adjustmentId: String,
) : ViewModel() {
    val uiState = repository.observeBalanceAdjustment(adjustmentId)
        .map { BalanceAdjustmentDetailUiState(isLoading = false, adjustment = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BalanceAdjustmentDetailUiState(),
        )

    suspend fun delete(adjustmentId: String): Result<Unit> = runCatching {
        repository.deleteBalanceAdjustment(adjustmentId)
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivityViewModel(private val repository: FinanceRepository) : ViewModel() {
    val selectedKind = MutableStateFlow<ActivityKind?>(null)
    val selectedAccountId = MutableStateFlow<String?>(null)
    val selectedCurrency = MutableStateFlow<String?>(null)
    val selectedCategoryId = MutableStateFlow<String?>(null)
    val fromDate = MutableStateFlow<String?>(null)
    val toDate = MutableStateFlow<String?>(null)
    private val refreshRequests = MutableStateFlow(0L)
    val accounts = repository.observeActiveAccounts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val categories = repository.observeCategories().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val activity: Flow<PagingData<ActivityItem>> = combine(
        selectedKind,
        selectedAccountId,
    ) { kind, accountId -> ActivityFilter(kind = kind, accountId = accountId) }
        .combine(selectedCurrency) { filter, currency -> filter.copy(currency = currency) }
        .combine(selectedCategoryId) { filter, categoryId -> filter.copy(categoryId = categoryId) }
        .combine(fromDate) { filter, from -> filter.copy(fromDate = from) }
        .combine(toDate) { filter, to -> filter.copy(toDate = to) }
        .combine(refreshRequests) { filter, _ -> filter }
        .flatMapLatest { filter ->
            repository.activityPager(filter)
        }.cachedIn(viewModelScope)

    fun applyFilter(filter: ActivityFilter) {
        selectedKind.value = filter.kind
        selectedAccountId.value = filter.accountId
        selectedCurrency.value = filter.currency
        selectedCategoryId.value = filter.categoryId
        fromDate.value = filter.fromDate
        toDate.value = filter.toDate
    }

    fun clearExtendedFilters() {
        selectedCurrency.value = null
        selectedCategoryId.value = null
        fromDate.value = null
        toDate.value = null
    }

    fun refresh() {
        refreshRequests.update { it + 1 }
    }
}

data class DebtsUiState(
    val debts: List<DebtSummary> = emptyList(),
    val isLoading: Boolean = true,
)

class DebtsViewModel(repository: FinanceRepository) : ViewModel() {
    val uiState = repository.observeDebts()
        .map { DebtsUiState(it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsUiState())
}

data class DebtDetailUiState(
    val debt: DebtSummary? = null,
    val repayments: List<DebtRepaymentSummary> = emptyList(),
    val isLoading: Boolean = true,
)

class DebtDetailViewModel(repository: FinanceRepository, debtId: String) : ViewModel() {
    val uiState = combine(
        repository.observeDebt(debtId),
        repository.observeDebtRepayments(debtId),
    ) { debt, repayments -> DebtDetailUiState(debt, repayments, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtDetailUiState())
}

inline fun <reified T : ViewModel> viewModelFactory(
    crossinline create: () -> T,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create() }
}
