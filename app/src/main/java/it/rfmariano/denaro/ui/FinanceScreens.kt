@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.TransactionType
import com.composables.icons.lucide.R as LucideR

@Composable
fun HomeRouteContent(
    viewModel: HomeViewModel,
    amountsVisible: Boolean,
    onToggleAmounts: () -> Unit,
    onAccountClick: (String) -> Unit,
    onAddAccount: () -> Unit,
    onSeeAllActivity: () -> Unit,
    onActivityClick: (ActivityItem) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
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
        if (state.accounts.isEmpty()) {
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    BalanceBand(state.totalsByCurrency, amountsVisible)
                }
                item {
                    SectionHeader(stringResource(R.string.accounts))
                }
                items(state.accounts, key = AccountSummary::id) { account ->
                    AccountRow(
                        account = account,
                        amountsVisible = amountsVisible,
                        onClick = { onAccountClick(account.id) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp))
                }
                item {
                    SectionHeader(
                        title = stringResource(R.string.recent_activity),
                        action = {
                            TextButton(onClick = onSeeAllActivity) {
                                Text(stringResource(R.string.see_all))
                            }
                        },
                    )
                }
                if (state.recentActivity.isEmpty()) {
                    item {
                        EmptyState(
                            icon = LucideR.drawable.lucide_ic_receipt,
                            title = stringResource(R.string.no_activity),
                            description = stringResource(R.string.no_activity_description),
                        )
                    }
                } else {
                    items(state.recentActivity, key = ActivityItem::id) { activity ->
                        ActivityRow(
                            item = activity,
                            amountsVisible = amountsVisible,
                            onClick = { onActivityClick(activity) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 58.dp))
                    }
                }
            }
        }
    }
}

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
        if (state.active.isEmpty()) {
            EmptyState(
                icon = LucideR.drawable.lucide_ic_wallet,
                title = stringResource(R.string.no_accounts),
                description = stringResource(R.string.no_accounts_description),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
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

@Composable
fun AccountDetailRouteContent(
    viewModel: AccountDetailViewModel,
    amountsVisible: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
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
        if (account != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
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
                            modifier = Modifier.fillMaxWidth()
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
        if (accounts.isEmpty()) {
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
) {
    val selectedKind by viewModel.selectedKind.collectAsStateWithLifecycle()
    val selectedAccountId by viewModel.selectedAccountId.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activity = viewModel.activity.collectAsLazyPagingItems()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.activity)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddActivity) {
                Icon(
                    painterResource(LucideR.drawable.lucide_ic_plus),
                    contentDescription = stringResource(R.string.add_activity),
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedKind == null,
                    onClick = { viewModel.selectedKind.value = null },
                    label = { Text(stringResource(R.string.all)) },
                )
                ActivityKind.entries.forEach { kind ->
                    FilterChip(
                        selected = selectedKind == kind,
                        onClick = {
                            viewModel.selectedKind.value =
                                if (selectedKind == kind) null else kind
                        },
                        label = {
                            Text(
                                stringResource(
                                    when (kind) {
                                        ActivityKind.INCOME -> R.string.income
                                        ActivityKind.EXPENSE -> R.string.expense
                                        ActivityKind.TRANSFER -> R.string.transfer
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

@Composable
private fun ActivityList(
    activity: LazyPagingItems<ActivityItem>,
    amountsVisible: Boolean,
    perspectiveAccountId: String?,
    onActivityClick: (ActivityItem) -> Unit,
) {
    if (activity.loadState.refresh is LoadState.NotLoading && activity.itemCount == 0) {
        EmptyState(
            icon = LucideR.drawable.lucide_ic_receipt,
            title = stringResource(R.string.no_activity),
            description = stringResource(R.string.no_activity_description),
        )
        return
    }
    LazyColumn(
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
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(LucideR.drawable.lucide_ic_arrow_left),
            contentDescription = stringResource(R.string.back),
        )
    }
}
