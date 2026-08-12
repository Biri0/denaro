@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.rfmariano.denaro.ui

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.DebtSummary
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.finance.UNCATEGORIZED_CATEGORY_FILTER
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.TransactionType
import com.composables.icons.lucide.R as LucideR

@Composable
fun HomeRouteContent(
    viewModel: HomeViewModel,
    amountsVisible: Boolean,
    onToggleAmounts: () -> Unit,
    onAddAccount: () -> Unit,
    onSettings: () -> Unit,
    onCategoryClick: (
        ActivityKind,
        String?,
        String,
        String,
        String,
        String?,
    ) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportDrawnWhen { isHomeFullyDrawn(state) }
    LaunchedEffect(state.accounts, state.selectedCurrency) {
        if (state.accounts.isNotEmpty() && state.accounts.none { it.currency == state.selectedCurrency }) {
            viewModel.selectedCurrency.value = state.accounts.first().currency
            viewModel.selectedAccountId.value = null
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            painter = painterResource(LucideR.drawable.lucide_ic_settings),
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                    IconButton(onClick = onToggleAmounts) {
                        Icon(
                            painter = painterResource(
                                if (amountsVisible) {
                                    LucideR.drawable.lucide_ic_eye
                                } else {
                                    LucideR.drawable.lucide_ic_eye_off
                                },
                            ),
                            contentDescription = stringResource(
                                if (amountsVisible) {
                                    R.string.hide_amounts
                                } else {
                                    R.string.show_amounts
                                },
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> HomeLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            shouldShowEmptyState(state.isLoading, state.accounts.size) -> {
                EmptyState(
                    icon = LucideR.drawable.lucide_ic_wallet,
                    title = stringResource(R.string.no_accounts),
                    description = stringResource(R.string.no_accounts_description),
                    modifier = Modifier.padding(padding),
                    action = {
                        Button(onClick = onAddAccount) {
                            Text(stringResource(R.string.add_account))
                        }
                    },
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        BalanceBand(
                            state.totalsByCurrency.filterKeys { it == state.selectedCurrency },
                            amountsVisible,
                        )
                    }
                    item {
                        DashboardControls(
                            state = state,
                            onCurrency = {
                                viewModel.selectedCurrency.value = it
                                viewModel.selectedAccountId.value = null
                            },
                            onAccount = { viewModel.selectedAccountId.value = it },
                            onPreviousMonth = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth,
                        )
                    }
                    if (state.isDashboardLoading) {
                        item { DashboardLoadingSkeleton() }
                    } else {
                        state.dashboard?.let { dashboard ->
                            item { DashboardSummary(dashboard, amountsVisible) }
                            item {
                                MonthlyCashFlowChart(
                                    dashboard.months,
                                    dashboard.filter.currency,
                                    amountsVisible
                                )
                            }
                            item {
                                CategoryBreakdown(
                                    dashboard = dashboard,
                                    amountsVisible = amountsVisible,
                                    onCategoryClick = { kind, categoryId ->
                                        val month =
                                            java.time.YearMonth.parse(dashboard.filter.selectedMonth)
                                        onCategoryClick(
                                            kind,
                                            categoryId ?: UNCATEGORIZED_CATEGORY_FILTER,
                                            month.atDay(1).toString(),
                                            month.plusMonths(1).atDay(1).toString(),
                                            dashboard.filter.currency,
                                            dashboard.filter.accountId,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun isHomeFullyDrawn(state: HomeUiState): Boolean =
    !state.isLoading && (state.accounts.isEmpty() || !state.isDashboardLoading)

@Composable
private fun BalanceBand(
    totals: Map<String, Long>,
    amountsVisible: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.balances),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        totals.forEach { (currency, amount) ->
            Text(
                text = if (amountsVisible) {
                    Money.format(amount, currency)
                } else {
                    stringResource(R.string.amount_hidden)
                },
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
fun AccountsRouteContent(
    viewModel: AccountsViewModel,
    amountsVisible: Boolean,
    onAccountClick: (String) -> Unit,
    onAddAccount: () -> Unit,
    onArchivedAccounts: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts)) },
                actions = {
                    IconButton(onClick = onArchivedAccounts) {
                        Icon(
                            painterResource(LucideR.drawable.lucide_ic_archive),
                            contentDescription = stringResource(R.string.archived_accounts),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAccount) {
                Icon(
                    painterResource(LucideR.drawable.lucide_ic_plus),
                    contentDescription = stringResource(R.string.add_account),
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> AccountListLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            shouldShowEmptyState(state.isLoading, state.active.size) -> {
                EmptyState(
                    icon = LucideR.drawable.lucide_ic_wallet,
                    title = stringResource(R.string.no_accounts),
                    description = stringResource(R.string.no_accounts_description),
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(state.active, key = AccountSummary::id) { account ->
                        AccountRow(
                            account = account,
                            amountsVisible = amountsVisible,
                            onClick = { onAccountClick(account.id) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 58.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AccountDetailRouteContent(
    viewModel: AccountDetailViewModel,
    amountsVisible: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onEditSchedule: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val account = state.account
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.name.orEmpty()) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    account?.let {
                        IconButton(onClick = { onEdit(it.id) }) {
                            Icon(
                                painterResource(LucideR.drawable.lucide_ic_pencil),
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            AccountDetailLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else if (account != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    ) {
                        Text(
                            stringResource(R.string.current_balance),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (amountsVisible) {
                                Money.format(account.balanceMinor, account.currency)
                            } else {
                                stringResource(R.string.amount_hidden)
                            },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "${stringResource(R.string.opening_balance)}: " +
                                    if (amountsVisible) {
                                        Money.format(account.openingBalanceMinor, account.currency)
                                    } else {
                                        stringResource(R.string.amount_hidden)
                                    },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.recurringRules.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.scheduled)) }
                    items(state.recurringRules, key = { it.id }) { rule ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditSchedule(rule.id) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Text(
                                rule.description ?: stringResource(
                                    if (rule.transactionType == TransactionType.INCOME) {
                                        R.string.income
                                    } else {
                                        R.string.expense
                                    },
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.recurs_every,
                                    rule.intervalCount,
                                    stringResource(
                                        when (rule.frequency) {
                                            RecurrenceFrequency.DAILY -> R.string.daily
                                            RecurrenceFrequency.WEEKLY -> R.string.weekly
                                            RecurrenceFrequency.MONTHLY -> R.string.monthly
                                            RecurrenceFrequency.YEARLY -> R.string.yearly
                                        },
                                    ),
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.next_occurrence,
                                    rule.nextOccurrenceAt.formattedDate(),
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (amountsVisible) {
                                    Money.format(rule.amountMinor, account.currency)
                                } else {
                                    stringResource(R.string.amount_hidden)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!rule.isActive) {
                                Text(
                                    stringResource(R.string.paused),
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ArchivedAccountsRouteContent(
    accounts: List<AccountSummary>,
    isLoading: Boolean,
    amountsVisible: Boolean,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archived_accounts)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (isLoading) {
            AccountListLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else if (shouldShowEmptyState(isLoading, accounts.size)) {
            EmptyState(
                icon = LucideR.drawable.lucide_ic_archive,
                title = stringResource(R.string.no_archived_accounts),
                description = "",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(accounts, key = AccountSummary::id) { account ->
                    AccountRow(
                        account = account,
                        amountsVisible = amountsVisible,
                        onClick = {},
                        trailingAction = {
                            TextButton(onClick = { onRestore(account.id) }) {
                                Text(stringResource(R.string.restore))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityRouteContent(
    viewModel: ActivityViewModel,
    amountsVisible: Boolean,
    onAddActivity: () -> Unit,
    onActivityClick: (ActivityItem) -> Unit,
    debts: List<DebtSummary> = emptyList(),
    onAddDebt: () -> Unit = {},
    onDebtClick: (String) -> Unit = {},
) {
    var showDebts by rememberSaveable { mutableStateOf(false) }
    val selectedKind by viewModel.selectedKind.collectAsStateWithLifecycle()
    val selectedAccountId by viewModel.selectedAccountId.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val fromDate by viewModel.fromDate.collectAsStateWithLifecycle()
    val toDate by viewModel.toDate.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val activity = viewModel.activity.collectAsLazyPagingItems()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.activity)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = if (showDebts) onAddDebt else onAddActivity) {
                Icon(
                    painterResource(LucideR.drawable.lucide_ic_plus),
                    contentDescription = stringResource(if (showDebts) R.string.add_debt else R.string.add_activity),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                listOf(false, true).forEachIndexed { index, debtsSelected ->
                    SegmentedButton(
                        selected = showDebts == debtsSelected,
                        onClick = { showDebts = debtsSelected },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) {
                        Text(stringResource(if (debtsSelected) R.string.debts else R.string.movements))
                    }
                }
            }
            if (showDebts) {
                DebtsPanel(debts, amountsVisible, onAddDebt, onDebtClick)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedKind == null,
                        onClick = {
                            viewModel.selectedKind.value = null
                            viewModel.selectedCategoryId.value = null
                        },
                        label = { Text(stringResource(R.string.all)) },
                    )
                    ActivityKind.entries.filterNot { it == ActivityKind.DEBT }.forEach { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = {
                                val next = if (selectedKind == kind) null else kind
                                viewModel.selectedKind.value = next
                                if (next == null || next == ActivityKind.TRANSFER) {
                                    viewModel.selectedCategoryId.value = null
                                } else if (categories.find { it.id == selectedCategoryId }?.type?.name != next.name) {
                                    viewModel.selectedCategoryId.value = null
                                }
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (kind) {
                                            ActivityKind.INCOME -> R.string.income
                                            ActivityKind.EXPENSE -> R.string.expense
                                            ActivityKind.TRANSFER -> R.string.transfer
                                            ActivityKind.DEBT -> R.string.debt
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                if (selectedKind == ActivityKind.INCOME || selectedKind == ActivityKind.EXPENSE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            CategorySelector(
                                categories = categories.filter { it.type.name == selectedKind?.name },
                                selectedId = selectedCategoryId,
                                onSelected = { viewModel.selectedCategoryId.value = it },
                            )
                        }
                    }
                }
                if (selectedCurrency != null || fromDate != null || toDate != null || selectedCategoryId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            listOfNotNull(
                                selectedCurrency,
                                fromDate?.let { "$it – ${toDate.orEmpty()}" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::clearExtendedFilters) {
                            Text(stringResource(R.string.clear_filter))
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AccountSelector(
                            label = stringResource(R.string.filter_account),
                            accounts = accounts,
                            selectedId = selectedAccountId.orEmpty(),
                            onSelected = { viewModel.selectedAccountId.value = it },
                        )
                    }
                    if (selectedAccountId != null) {
                        TextButton(onClick = { viewModel.selectedAccountId.value = null }) {
                            Text(stringResource(R.string.clear_filter))
                        }
                    }
                }
                ActivityList(
                    activity = activity,
                    amountsVisible = amountsVisible,
                    perspectiveAccountId = selectedAccountId,
                    onActivityClick = onActivityClick,
                )
            }
        }
    }
}

@Composable
private fun ActivityList(
    activity: LazyPagingItems<ActivityItem>,
    amountsVisible: Boolean,
    perspectiveAccountId: String?,
    onActivityClick: (ActivityItem) -> Unit,
) {
    when (activity.loadState.refresh) {
        is LoadState.Loading -> {
            ActivityLoadingSkeleton(modifier = Modifier.fillMaxSize())
            return
        }

        is LoadState.Error -> {
            EmptyState(
                icon = LucideR.drawable.lucide_ic_receipt,
                title = stringResource(R.string.activity_load_failed),
                description = "",
                action = {
                    Button(onClick = activity::retry) {
                        Text(stringResource(R.string.retry))
                    }
                },
            )
            return
        }

        is LoadState.NotLoading -> {
            if (activity.itemCount == 0) {
                EmptyState(
                    icon = LucideR.drawable.lucide_ic_receipt,
                    title = stringResource(R.string.no_activity),
                    description = stringResource(R.string.no_activity_description),
                )
                return
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(
            count = activity.itemCount,
            key = { index -> activity[index]?.id ?: index },
        ) { index ->
            activity[index]?.let { item ->
                if (index == 0 || activity[index - 1]?.localDate != item.localDate) {
                    Text(
                        text = item.occurredAt.formattedDate(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 18.dp,
                            bottom = 6.dp,
                        ),
                    )
                }
                ActivityRow(
                    item = item,
                    amountsVisible = amountsVisible,
                    perspectiveAccountId = perspectiveAccountId,
                    showTransferSign = false,
                    onClick = { onActivityClick(item) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 58.dp))
            }
        }
        when (activity.loadState.append) {
            is LoadState.Loading -> {
                item {
                    ActivityLoadingSkeleton(rowCount = 2)
                }
            }

            is LoadState.Error -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = activity::retry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            is LoadState.NotLoading -> Unit
        }
    }
}

internal fun shouldShowEmptyState(isLoading: Boolean, itemCount: Int): Boolean =
    !isLoading && itemCount == 0

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(LucideR.drawable.lucide_ic_arrow_left),
            contentDescription = stringResource(R.string.back),
        )
    }
}
