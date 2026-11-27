@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package it.rfmariano.denaro.ui

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.ActivityItem
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.BalanceAdjustmentSummary
import it.rfmariano.denaro.data.finance.BudgetProgress
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.finance.UNCATEGORIZED_CATEGORY_FILTER
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.ui.theme.denaroColors
import it.rfmariano.denaro.ui.theme.tabularNumbers
import kotlinx.coroutines.launch

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
    onAddActivity: (ActivityKind) -> Unit = {},
    onAddDebt: () -> Unit = {},
    onBudgetClick: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    ReportDrawnWhen { isHomeFullyDrawn(state) }
    LaunchedEffect(state.accounts, state.selectedCurrency) {
        if (state.accounts.isNotEmpty() && state.accounts.none { it.currency == state.selectedCurrency }) {
            viewModel.selectedCurrency.value = state.accounts.first().currency
            viewModel.selectedAccountId.value = null
        }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ms_settings),
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                    IconButton(onClick = onToggleAmounts) {
                        Icon(
                            painter = painterResource(
                                if (amountsVisible) {
                                    R.drawable.ms_visibility
                                } else {
                                    R.drawable.ms_visibility_off
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> HomeLoadingSkeleton(
                    modifier = Modifier.fillMaxSize(),
                )

                shouldShowEmptyState(state.isLoading, state.accounts.size) -> {
                    EmptyState(
                        icon = R.drawable.ms_account_balance_wallet,
                        title = stringResource(R.string.no_accounts),
                        description = stringResource(R.string.no_accounts_description),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            Button(onClick = onAddAccount) {
                                Text(stringResource(R.string.add_account))
                            }
                        },
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp),
                    ) {
                        item {
                            BalanceBand(
                                state.totalsByCurrency.filterKeys { it == state.selectedCurrency },
                                state.savingsTotalsByCurrency.filterKeys {
                                    it == state.selectedCurrency
                                },
                                state.accounts,
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
                                if (state.budgets.any {
                                        it.budget.currency == dashboard.filter.currency
                                    }
                                ) {
                                    item {
                                        BudgetHomeSection(
                                            budgets = state.budgets,
                                            currency = dashboard.filter.currency,
                                            amountsVisible = amountsVisible,
                                            onBudgetClick = onBudgetClick,
                                        )
                                    }
                                }
                                item {
                                    MonthlyCashFlowChart(
                                        dashboard.months,
                                        dashboard.filter.currency,
                                        dashboard.fractionDigits,
                                        amountsVisible,
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
                    val expenseLabel = stringResource(R.string.expense)
                    val incomeLabel = stringResource(R.string.income)
                    val transferLabel = stringResource(R.string.transfer)
                    val debtLabel = stringResource(R.string.debt)
                    DenaroQuickAddFabMenu(
                        actions = listOf(
                            FabMenuAction(expenseLabel, R.drawable.ms_arrow_upward) {
                                onAddActivity(ActivityKind.EXPENSE)
                            },
                            FabMenuAction(incomeLabel, R.drawable.ms_arrow_downward) {
                                onAddActivity(ActivityKind.INCOME)
                            },
                            FabMenuAction(transferLabel, R.drawable.ms_swap_vert) {
                                onAddActivity(ActivityKind.TRANSFER)
                            },
                            FabMenuAction(debtLabel, R.drawable.ms_handshake) {
                                onAddDebt()
                            },
                        ),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
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
    savingsTotals: Map<String, Long>,
    accounts: List<AccountSummary>,
    amountsVisible: Boolean,
) {
    val hiddenAmount = stringResource(R.string.amount_hidden)
    DenaroSectionCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        contentPadding = PaddingValues(22.dp),
    ) {
        Text(
            text = stringResource(R.string.balances),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
        )
        Spacer(Modifier.height(8.dp))
        (totals.keys + savingsTotals.keys).sorted().forEach { currency ->
            val fractionDigits = accounts.firstOrNull { it.currency == currency }?.fractionDigits
                ?: Money.fractionDigitsForCurrency(currency)

            fun amountOf(value: Long?) = if (amountsVisible) {
                Money.format(value ?: 0L, currency, fractionDigits)
            } else {
                hiddenAmount
            }

            val savings = savingsTotals[currency]
            if (savings != null) {
                Text(
                    text = stringResource(R.string.available),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                Text(
                    text = amountOf(totals[currency]),
                    style = MaterialTheme.typography.displaySmallEmphasized.tabularNumbers(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ms_savings),
                            contentDescription = null,
                            modifier = Modifier.height(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.saved),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = amountOf(savings),
                            style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                        )
                    }
                }
            } else {
                Text(
                    text = amountOf(totals[currency]),
                    style = MaterialTheme.typography.displaySmallEmphasized.tabularNumbers(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BudgetHomeSection(
    budgets: List<BudgetProgress>,
    currency: String,
    amountsVisible: Boolean,
    onBudgetClick: (String) -> Unit,
) {
    val visible = budgets.filter { it.budget.currency == currency }
    if (visible.isEmpty()) return
    val hiddenAmount = stringResource(R.string.amount_hidden)
    val spentLabel = stringResource(R.string.budget_spent)
    val overLabel = stringResource(R.string.over_budget)
    val positiveColor = MaterialTheme.denaroColors.positive
    val warningColor = MaterialTheme.denaroColors.warning

    DenaroSectionCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(20.dp),
    ) {
        Text(
            text = stringResource(R.string.budgets),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visible.forEach { progress ->
                val fraction = progress.fraction
                fun amountOf(minor: Long) = if (amountsVisible) {
                    Money.format(minor, currency, progress.fractionDigits)
                } else {
                    hiddenAmount
                }

                val spentText = amountOf(progress.spentMinor)
                val limitText = amountOf(progress.budget.amountMinor)
                val rowColor = when {
                    progress.isOver -> MaterialTheme.colorScheme.error
                    fraction >= 0.85f -> warningColor
                    else -> positiveColor
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onBudgetClick(progress.budget.categoryId) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(
                        progress.budget.categoryIconName ?: "circle_help",
                        progress.budget.categoryColorIndex,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = progress.budget.categoryName
                                    ?: stringResource(R.string.no_category),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = limitText,
                                style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                            )
                        }
                        DenaroLinearMeter(
                            progress = fraction,
                            color = rowColor,
                            height = 8.dp,
                        )
                        Text(
                            text = if (progress.isOver) {
                                "$overLabel · $spentText"
                            } else {
                                "$spentLabel: $spentText / $limitText"
                            },
                            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                            color = rowColor,
                        )
                    }
                }
            }
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.accounts),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onArchivedAccounts) {
                        Icon(
                            painterResource(R.drawable.ms_archive),
                            contentDescription = stringResource(R.string.archived_accounts),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAccount,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painterResource(R.drawable.ms_add),
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
                    icon = R.drawable.ms_account_balance_wallet,
                    title = stringResource(R.string.no_accounts),
                    description = stringResource(R.string.no_accounts_description),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    action = {
                        Button(onClick = onAddAccount) {
                            Text(stringResource(R.string.add_account))
                        }
                    },
                )
            }

            else -> {
                val accounts = state.active
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 104.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    itemsIndexed(accounts, key = { _, item -> item.id }) { index, account ->
                        AccountRow(
                            account = account,
                            amountsVisible = amountsVisible,
                            shapes = ListItemDefaults.segmentedShapes(index, accounts.size),
                            onClick = { onAccountClick(account.id) },
                        )
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
    onMessage: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val account = state.account
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showSetBalance by rememberSaveable { mutableStateOf(false) }
    var isSavingBalance by remember { mutableStateOf(false) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val adjustedMessage = stringResource(R.string.balance_adjusted)
    val deletedMessage = stringResource(R.string.account_deleted)
    val deleteFailedMessage = stringResource(R.string.account_delete_failed)
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var isArchiving by remember { mutableStateOf(false) }
    var archiveError by remember { mutableStateOf<String?>(null) }
    val archivedMessage = stringResource(R.string.account_archived)
    val archiveFailedMessage = stringResource(R.string.account_archive_failed)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(account?.name.orEmpty()) },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                actions = {
                    account?.let {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above,
                            ),
                            tooltip = {
                                PlainTooltip { Text(stringResource(R.string.edit)) }
                            },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(onClick = { onEdit(it.id) }) {
                                Icon(
                                    painterResource(R.drawable.ms_edit),
                                    contentDescription = stringResource(R.string.edit),
                                )
                            }
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above,
                            ),
                            tooltip = {
                                PlainTooltip { Text(stringResource(R.string.archive)) }
                            },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = { showArchive = true },
                                modifier = Modifier.testTag("archive_account_action"),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ms_archive),
                                    contentDescription = stringResource(R.string.archive),
                                )
                            }
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above,
                            ),
                            tooltip = {
                                PlainTooltip { Text(stringResource(R.string.delete)) }
                            },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = { showDelete = true },
                                modifier = Modifier.testTag("delete_account_action"),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ms_delete),
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
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
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 32.dp,
                ),
            ) {
                item {
                    val heroContainer = if (account.isSavings) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                    val heroContent = if (account.isSavings) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    DenaroSectionCard(
                        containerColor = heroContainer,
                        contentColor = heroContent,
                        contentPadding = PaddingValues(22.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.current_balance),
                            style = MaterialTheme.typography.labelLarge,
                            color = heroContent.copy(alpha = 0.78f),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (amountsVisible) {
                                Money.format(
                                    account.balanceMinor,
                                    account.currency,
                                    account.fractionDigits,
                                )
                            } else {
                                stringResource(R.string.amount_hidden)
                            },
                            style = MaterialTheme.typography.displaySmallEmphasized.tabularNumbers(),
                        )
                        if (account.isSavings) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.savings_account_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = heroContent.copy(alpha = 0.85f),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "${stringResource(R.string.opening_balance)}: " +
                                    if (amountsVisible) {
                                        Money.format(
                                            account.openingBalanceMinor,
                                            account.currency,
                                            account.fractionDigits,
                                        )
                                    } else {
                                        stringResource(R.string.amount_hidden)
                                    },
                            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                            color = heroContent.copy(alpha = 0.82f),
                        )
                        if (account.archivedAt == null) {
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = { showSetBalance = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.testTag("set_balance_action"),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ms_balance),
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.set_balance))
                            }
                        }
                    }
                }
                if (state.recurringRules.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.scheduled),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    itemsIndexed(
                        state.recurringRules,
                        key = { _, rule -> rule.id }) { index, rule ->
                        SegmentedListItem(
                            onClick = { onEditSchedule(rule.id) },
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = state.recurringRules.size,
                            ),
                            colors = denaroSegmentedItemColors(),
                            modifier = Modifier.padding(
                                bottom = if (index < state.recurringRules.lastIndex) {
                                    ListItemDefaults.SegmentedGap
                                } else {
                                    0.dp
                                },
                            ),
                            leadingContent = {
                                DenaroIconBadge(
                                    icon = R.drawable.ms_autorenew,
                                    containerColor = if (rule.isActive) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    contentColor = if (rule.isActive) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                    )
                                    Text(
                                        stringResource(
                                            R.string.next_occurrence,
                                            rule.nextOccurrenceAt.formattedDate(),
                                        ),
                                    )
                                    if (!rule.isActive) {
                                        Text(
                                            stringResource(R.string.paused),
                                            color = MaterialTheme.denaroColors.warning,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                AmountText(
                                    amountMinor = rule.amountMinor,
                                    currency = account.currency,
                                    fractionDigits = account.fractionDigits,
                                    amountsVisible = amountsVisible,
                                    color = if (rule.transactionType == TransactionType.INCOME) {
                                        MaterialTheme.denaroColors.positive
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                        ) {
                            Text(
                                rule.description ?: stringResource(
                                    if (rule.transactionType == TransactionType.INCOME) {
                                        R.string.income
                                    } else {
                                        R.string.expense
                                    },
                                ),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSetBalance && account != null) {
        SetBalanceDialog(
            account = account,
            amountsVisible = amountsVisible,
            isSaving = isSavingBalance,
            submissionError = balanceError,
            onDismiss = {
                if (!isSavingBalance) {
                    showSetBalance = false
                    balanceError = null
                }
            },
            onConfirm = { targetBalance ->
                if (isSavingBalance) return@SetBalanceDialog
                isSavingBalance = true
                balanceError = null
                scope.launch {
                    viewModel.setBalance(targetBalance)
                        .onSuccess {
                            showSetBalance = false
                            onMessage(adjustedMessage)
                        }
                        .onFailure { balanceError = it.message }
                    isSavingBalance = false
                }
            },
        )
    }

    if (showArchive && account != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isArchiving) {
                    showArchive = false
                    archiveError = null
                }
            },
            title = { Text(stringResource(R.string.archive_account)) },
            text = {
                Column {
                    Text(stringResource(R.string.archive_account_message))
                    archiveError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isArchiving) return@TextButton
                        isArchiving = true
                        archiveError = null
                        scope.launch {
                            viewModel.archive()
                                .onSuccess {
                                    showArchive = false
                                    onMessage(archivedMessage)
                                    onBack()
                                }
                                .onFailure { archiveError = archiveFailedMessage }
                            isArchiving = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.archive))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isArchiving) showArchive = false
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDelete && account != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDelete = false
                    deleteError = null
                }
            },
            title = { Text(stringResource(R.string.delete_account)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_account_message))
                    deleteError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isDeleting) return@TextButton
                        isDeleting = true
                        deleteError = null
                        scope.launch {
                            viewModel.delete()
                                .onSuccess {
                                    showDelete = false
                                    onMessage(deletedMessage)
                                    onBack()
                                }
                                .onFailure { deleteError = deleteFailedMessage }
                            isDeleting = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isDeleting) showDelete = false
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SetBalanceDialog(
    account: AccountSummary,
    amountsVisible: Boolean,
    isSaving: Boolean,
    submissionError: String?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var value by rememberSaveable(account.id) { mutableStateOf("") }
    val parsed = remember(value) {
        runCatching {
            Money.parseMinorUnits(
                value,
                account.fractionDigits,
                allowNegative = true,
            )
        }.getOrNull()
    }
    val difference = remember(parsed, account.balanceMinor) {
        parsed?.let { runCatching { Math.subtractExact(it, account.balanceMinor) }.getOrNull() }
    }
    val unchanged = difference == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_balance)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.set_balance_description))
                OutlinedTextField(
                    shape = MaterialTheme.shapes.large,
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.new_balance)) },
                    suffix = { Text(account.currency) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.DecimalSigned),
                    singleLine = true,
                    isError = submissionError != null || (value.isNotBlank() && parsed == null),
                    supportingText = {
                        when {
                            submissionError != null -> Text(submissionError)
                            unchanged -> Text(stringResource(R.string.balance_already_matches))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_balance_input"),
                )
                Text(
                    text = "${stringResource(R.string.current_balance)}: " + if (amountsVisible) {
                        Money.format(
                            account.balanceMinor,
                            account.currency,
                            account.fractionDigits,
                        )
                    } else {
                        stringResource(R.string.amount_hidden)
                    },
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (difference != null) {
                    Text(
                        text = "${stringResource(R.string.difference)}: " + if (amountsVisible) {
                            Money.format(
                                difference,
                                account.currency,
                                account.fractionDigits,
                            )
                        } else {
                            stringResource(R.string.amount_hidden)
                        },
                        style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && !unchanged && difference != null && !isSaving,
                onClick = { parsed?.let(onConfirm) },
                modifier = Modifier.testTag("set_balance_confirm"),
            ) { Text(stringResource(R.string.set_balance)) }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun BalanceAdjustmentDetailRouteContent(
    viewModel: BalanceAdjustmentDetailViewModel,
    amountsVisible: Boolean,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.balance_adjustment_deleted)

    BalanceAdjustmentDetailScreen(
        state = state,
        amountsVisible = amountsVisible,
        isDeleting = isDeleting,
        onBack = onBack,
        onDelete = { adjustmentId ->
            if (isDeleting) return@BalanceAdjustmentDetailScreen
            isDeleting = true
            scope.launch {
                viewModel.delete(adjustmentId)
                    .onSuccess {
                        onDeleted()
                        onMessage(deletedMessage)
                    }
                    .onFailure {
                        isDeleting = false
                        onMessage(it.message.orEmpty())
                    }
            }
        },
    )
}

@Composable
fun BalanceAdjustmentDetailScreen(
    state: BalanceAdjustmentDetailUiState,
    amountsVisible: Boolean,
    isDeleting: Boolean,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val adjustment = state.adjustment
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.balance_adjustment)) },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            ActivityLoadingSkeleton(Modifier.padding(padding))
        } else if (adjustment != null) {
            BalanceAdjustmentDetails(
                adjustment = adjustment,
                amountsVisible = amountsVisible,
                onDelete = { confirmDelete = true },
                modifier = Modifier.padding(padding),
            )
        }
    }
    if (confirmDelete && adjustment != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) confirmDelete = false
            },
            title = { Text(stringResource(R.string.delete_balance_adjustment)) },
            text = { Text(stringResource(R.string.delete_balance_adjustment_message)) },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { if (!isDeleting) onDelete(adjustment.id) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.testTag("delete_adjustment_confirm"),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { confirmDelete = false },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BalanceAdjustmentDetails(
    adjustment: BalanceAdjustmentSummary,
    amountsVisible: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hidden = stringResource(R.string.amount_hidden)
    fun amount(value: Long): String = if (amountsVisible) {
        Money.format(value, adjustment.currency, adjustment.fractionDigits)
    } else {
        hidden
    }

    val deltaColor = if (adjustment.deltaMinor >= 0L) {
        MaterialTheme.denaroColors.positive
    } else {
        MaterialTheme.colorScheme.error
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DenaroSectionCard(contentPadding = PaddingValues(22.dp)) {
            Text(
                text = adjustment.accountName,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = adjustment.occurredAt.formattedDate(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.difference),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = amount(adjustment.deltaMinor),
                style = MaterialTheme.typography.headlineLargeEmphasized.tabularNumbers(),
                color = deltaColor,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "${stringResource(R.string.previous_balance)}: ${amount(adjustment.balanceBeforeMinor)}",
                style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${stringResource(R.string.new_balance)}: ${amount(adjustment.balanceAfterMinor)}",
                style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
            )
        }
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(R.string.delete))
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.archived_accounts)) },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
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
                icon = R.drawable.ms_archive,
                title = stringResource(R.string.no_archived_accounts),
                description = "",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(accounts, key = { _, account -> account.id }) { index, account ->
                    AccountRow(
                        account = account,
                        amountsVisible = amountsVisible,
                        shapes = ListItemDefaults.segmentedShapes(index, accounts.size),
                        onClick = {},
                        trailingAction = {
                            FilledTonalButton(onClick = { onRestore(account.id) }) {
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.activity),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddActivity,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painterResource(R.drawable.ms_add),
                    contentDescription = stringResource(R.string.add_activity),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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
                                        ActivityKind.ADJUSTMENT -> R.string.balance_adjustments
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
                    verticalAlignment = Alignment.CenterVertically,
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        listOfNotNull(
                            selectedCurrency,
                            fromDate?.let { "$it – ${toDate.orEmpty()}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
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
    when (activity.loadState.refresh) {
        is LoadState.Loading -> {
            ActivityLoadingSkeleton(modifier = Modifier.fillMaxSize())
            return
        }

        is LoadState.Error -> {
            EmptyState(
                icon = R.drawable.ms_receipt,
                title = stringResource(R.string.activity_load_failed),
                description = "",
                modifier = Modifier.fillMaxSize(),
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
                    icon = R.drawable.ms_receipt,
                    title = stringResource(R.string.no_activity),
                    description = stringResource(R.string.no_activity_description),
                    modifier = Modifier.fillMaxSize(),
                )
                return
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 104.dp,
        ),
    ) {
        items(
            count = activity.itemCount,
            key = { index -> activity[index]?.id ?: index },
        ) { index ->
            activity[index]?.let { item ->
                val prevDate = if (index > 0) activity[index - 1]?.localDate else null
                val nextDate =
                    if (index + 1 < activity.itemCount) activity[index + 1]?.localDate else null
                val isFirstInGroup = prevDate != item.localDate
                val isLastInGroup = nextDate != item.localDate

                val (groupIndex, groupCount) = when {
                    isFirstInGroup && isLastInGroup -> 0 to 1
                    isFirstInGroup -> 0 to 3
                    isLastInGroup -> 2 to 3
                    else -> 1 to 3
                }

                if (isFirstInGroup) {
                    Text(
                        text = item.occurredAt.formattedDate(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = 8.dp,
                            end = 8.dp,
                            top = if (index == 0) 8.dp else 18.dp,
                            bottom = 8.dp,
                        ),
                    )
                }
                ActivityRow(
                    item = item,
                    amountsVisible = amountsVisible,
                    perspectiveAccountId = perspectiveAccountId,
                    showTransferSign = false,
                    shapes = ListItemDefaults.segmentedShapes(groupIndex, groupCount),
                    onClick = { onActivityClick(item) },
                )
                if (!isLastInGroup) {
                    Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                }
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
            painterResource(R.drawable.ms_arrow_back),
            contentDescription = stringResource(R.string.back),
        )
    }
}
