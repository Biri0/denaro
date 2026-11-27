@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.CounterpartyInput
import it.rfmariano.denaro.data.finance.CounterpartySummary
import it.rfmariano.denaro.data.finance.DebtEntryDefaults
import it.rfmariano.denaro.data.finance.DebtInput
import it.rfmariano.denaro.data.finance.DebtRepaymentInput
import it.rfmariano.denaro.data.finance.DebtSummary
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.local.DebtDirection
import it.rfmariano.denaro.ui.theme.denaroColors
import it.rfmariano.denaro.ui.theme.tabularNumbers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

@Composable
fun DebtsRouteContent(
    viewModel: DebtsViewModel,
    amountsVisible: Boolean,
    onAddDebt: () -> Unit,
    onDebtClick: (String) -> Unit,
    onCounterparties: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.debts),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onCounterparties) {
                        Icon(
                            painter = painterResource(R.drawable.ms_group),
                            contentDescription = stringResource(R.string.counterparties),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDebt,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ms_add),
                    contentDescription = stringResource(R.string.add_debt),
                )
            }
        },
    ) { padding ->
        DebtsPanel(
            debts = state.debts,
            amountsVisible = amountsVisible,
            onAddDebt = onAddDebt,
            onDebtClick = onDebtClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
fun DebtsPanel(
    debts: List<DebtSummary>,
    amountsVisible: Boolean,
    onAddDebt: () -> Unit,
    onDebtClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var direction by rememberSaveable { mutableStateOf<DebtDirection?>(null) }
    var status by rememberSaveable { mutableStateOf(DebtStatusFilter.OPEN) }
    var selectedCounterpartyId by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val counterparties = remember(debts) { counterpartyFilterOptions(debts) }
    LaunchedEffect(counterparties, selectedCounterpartyId) {
        if (selectedCounterpartyId != null && counterparties.none { it.id == selectedCounterpartyId }) {
            selectedCounterpartyId = null
        }
    }
    val visible = remember(debts, direction, status, today, selectedCounterpartyId) {
        filterDebts(debts, direction, status, today, selectedCounterpartyId)
    }
    Column(modifier.fillMaxSize()) {
        val currencyTotals = remember(debts, selectedCounterpartyId) {
            debtCurrencyTotals(debts, selectedCounterpartyId)
        }
        currencyTotals.forEach { totals ->
            if (selectedCounterpartyId != null) {
                CounterpartyNetTotal(
                    totals = totals,
                    amountsVisible = amountsVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DebtTotal(
                        label = R.string.you_owe,
                        amount = totals.youOweMinor,
                        currency = totals.currency,
                        fractionDigits = totals.fractionDigits,
                        visible = amountsVisible,
                        amountColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    DebtTotal(
                        label = R.string.owed_to_you,
                        amount = totals.owedToYouMinor,
                        currency = totals.currency,
                        fractionDigits = totals.fractionDigits,
                        visible = amountsVisible,
                        amountColor = MaterialTheme.denaroColors.positive,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(null, DebtDirection.BORROWED, DebtDirection.LENT).forEach { option ->
                FilterChip(
                    selected = direction == option,
                    onClick = { direction = option },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    null -> R.string.all
                                    DebtDirection.BORROWED -> R.string.borrowed
                                    DebtDirection.LENT -> R.string.lent
                                },
                            ),
                        )
                    },
                )
            }
        }
        DenaroSegmentedChoiceRow(
            options = DebtStatusFilter.entries,
            selectedOption = status,
            onSelect = { status = it },
            label = { option ->
                stringResource(if (option == DebtStatusFilter.OPEN) R.string.open else R.string.settled)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        CounterpartyFilterRow(
            options = counterparties,
            selectedId = selectedCounterpartyId,
            onSelected = { selectedCounterpartyId = it },
        )
        if (visible.isEmpty()) {
            EmptyState(
                icon = R.drawable.ms_handshake,
                title = stringResource(R.string.no_debts),
                description = stringResource(R.string.no_debts_description),
                modifier = Modifier.fillMaxSize(),
                action = {
                    Button(onClick = onAddDebt) {
                        Text(stringResource(R.string.add_debt))
                    }
                },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(visible, key = { _, debt -> debt.id }) { index, debt ->
                    DebtRow(
                        debt = debt,
                        amountsVisible = amountsVisible,
                        shapes = ListItemDefaults.segmentedShapes(index, visible.size),
                        onClick = { onDebtClick(debt.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CounterpartyFilterRow(
    options: List<CounterpartyFilterOption>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            LabeledDropdownSelector(
                label = stringResource(R.string.filter_counterparty),
                placeholder = stringResource(R.string.all_people_and_organizations),
                selectedText = options.find { it.id == selectedId }?.name.orEmpty(),
                options = options.map { it.id to it.name },
                onSelected = onSelected,
                selectedId = selectedId,
            )
        }
        if (selectedId != null) {
            TextButton(onClick = { onSelected(null) }) {
                Text(stringResource(R.string.clear_filter))
            }
        }
    }
}

@Composable
private fun DebtTotal(
    label: Int,
    amount: Long,
    currency: String,
    fractionDigits: Int,
    visible: Boolean,
    amountColor: Color,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (visible) {
                    Money.format(amount, currency, fractionDigits)
                } else {
                    stringResource(R.string.amount_hidden)
                },
                style = MaterialTheme.typography.titleLargeEmphasized.tabularNumbers(),
                color = if (amount > 0L) amountColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CounterpartyNetTotal(
    totals: DebtCurrencyTotals,
    amountsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelRes = when {
        totals.netMinor > 0L && totals.hasBothDirections -> R.string.owed_to_you_net
        totals.netMinor > 0L -> R.string.owed_to_you
        totals.netMinor < 0L && totals.hasBothDirections -> R.string.you_owe_net
        totals.netMinor < 0L -> R.string.you_owe
        else -> R.string.net_even
    }
    val amountColor = when {
        totals.netMinor > 0L -> MaterialTheme.denaroColors.positive
        totals.netMinor < 0L -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val hidden = stringResource(R.string.amount_hidden)
    fun formatted(minor: Long): String = if (amountsVisible) {
        Money.format(minor, totals.currency, totals.fractionDigits)
    } else {
        hidden
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatted(abs(totals.netMinor)),
                style = MaterialTheme.typography.headlineSmallEmphasized.tabularNumbers(),
                color = amountColor,
            )
            if (totals.hasBothDirections) {
                Text(
                    text = "${stringResource(R.string.owed_to_you)} ${formatted(totals.owedToYouMinor)} · ${
                        stringResource(R.string.you_owe)
                    } ${formatted(totals.youOweMinor)}",
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DebtRow(
    debt: DebtSummary,
    amountsVisible: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val today = LocalDate.now()
    val isOverdue = debt.isOverdue(today)
    val isBorrowed = debt.direction == DebtDirection.BORROWED
    val badgeBg = when {
        debt.isSettled -> MaterialTheme.colorScheme.surfaceContainerHighest
        isOverdue -> MaterialTheme.colorScheme.errorContainer
        isBorrowed -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.denaroColors.positiveContainer
    }
    val badgeFg = when {
        debt.isSettled -> MaterialTheme.colorScheme.onSurfaceVariant
        isOverdue -> MaterialTheme.colorScheme.onErrorContainer
        isBorrowed -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.denaroColors.onPositiveContainer
    }
    val amountColor = when {
        debt.isSettled -> MaterialTheme.colorScheme.onSurfaceVariant
        isBorrowed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.denaroColors.positive
    }
    val repaidFraction = if (debt.principalMinor > 0L) {
        (debt.repaidMinor.toFloat() / debt.principalMinor.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = denaroSegmentedItemColors(),
        leadingContent = {
            DenaroIconBadge(
                icon = if (isBorrowed) R.drawable.ms_download else R.drawable.ms_upload,
                containerColor = badgeBg,
                contentColor = badgeFg,
                size = 42.dp,
                iconSize = 20.dp,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(if (isBorrowed) R.string.borrowed else R.string.lent),
                    style = MaterialTheme.typography.bodyMedium,
                )
                debt.dueDate?.let { due ->
                    val date = LocalDate.parse(due)
                    Text(
                        text = when {
                            isOverdue -> stringResource(R.string.overdue)
                            !debt.isSettled && date == today -> stringResource(R.string.due_today)
                            else -> stringResource(R.string.due_on, date.formattedDate())
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOverdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (debt.repaidMinor > 0L && !debt.isSettled) {
                    Spacer(Modifier.height(2.dp))
                    DenaroLinearMeter(
                        progress = repaidFraction,
                        color = if (isBorrowed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.denaroColors.positive
                        },
                        height = 5.dp,
                    )
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (amountsVisible) {
                        Money.format(
                            maxOf(0L, debt.outstandingMinor),
                            debt.currency,
                            debt.fractionDigits,
                        )
                    } else {
                        stringResource(R.string.amount_hidden)
                    },
                    style = MaterialTheme.typography.titleMediumEmphasized.tabularNumbers(),
                    color = amountColor,
                )
                Text(
                    text = stringResource(if (debt.isSettled) R.string.settled else R.string.outstanding),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(
            text = debt.counterpartyName,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DebtDetailScreen(
    state: DebtDetailUiState,
    accounts: List<AccountSummary>,
    amountsVisible: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddRepayment: (String) -> Unit,
    onEditRepayment: (String, String) -> Unit,
) {
    val debt = state.debt
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(debt?.counterpartyName.orEmpty()) },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                actions = {
                    debt?.let {
                        IconButton(onClick = { onEdit(it.id) }) {
                            Icon(
                                painterResource(R.drawable.ms_edit),
                                contentDescription = stringResource(R.string.edit_debt),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (debt != null) {
            val repaidFraction = if (debt.principalMinor > 0L) {
                (debt.repaidMinor.toFloat() / debt.principalMinor.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val heroContainer = if (debt.direction == DebtDirection.LENT) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val heroContent = if (debt.direction == DebtDirection.LENT) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }

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
                    DenaroSectionCard(
                        containerColor = heroContainer,
                        contentColor = heroContent,
                        contentPadding = PaddingValues(22.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (debt.direction == DebtDirection.BORROWED) {
                                    R.string.you_owe
                                } else {
                                    R.string.owed_to_you
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = heroContent.copy(alpha = 0.78f),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (amountsVisible) {
                                Money.format(
                                    maxOf(0L, debt.outstandingMinor),
                                    debt.currency,
                                    debt.fractionDigits,
                                )
                            } else {
                                stringResource(R.string.amount_hidden)
                            },
                            style = MaterialTheme.typography.displaySmallEmphasized.tabularNumbers(),
                        )
                        Spacer(Modifier.height(14.dp))
                        DenaroLinearMeter(
                            progress = repaidFraction,
                            color = if (debt.direction == DebtDirection.LENT) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.denaroColors.positive
                            },
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            height = 8.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${stringResource(R.string.principal)}: ${
                                    if (amountsVisible) {
                                        Money.format(
                                            debt.principalMinor,
                                            debt.currency,
                                            debt.fractionDigits,
                                        )
                                    } else {
                                        stringResource(R.string.amount_hidden)
                                    }
                                }",
                                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                            )
                            Text(
                                text = "${stringResource(R.string.repaid)}: ${
                                    if (amountsVisible) {
                                        Money.format(
                                            debt.repaidMinor,
                                            debt.currency,
                                            debt.fractionDigits,
                                        )
                                    } else {
                                        stringResource(R.string.amount_hidden)
                                    }
                                }",
                                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                            )
                        }
                        if (debt.overpaidMinor > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.overpaid_by,
                                    if (amountsVisible) {
                                        Money.format(
                                            debt.overpaidMinor,
                                            debt.currency,
                                            debt.fractionDigits,
                                        )
                                    } else {
                                        stringResource(R.string.amount_hidden)
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                                color = MaterialTheme.denaroColors.warning,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = debt.accountName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = heroContent.copy(alpha = 0.8f),
                        )
                        debt.dueDate?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.due_on,
                                    LocalDate.parse(it).formattedDate(),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = heroContent.copy(alpha = 0.8f),
                            )
                        }
                        debt.note?.takeIf(String::isNotBlank)?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = heroContent.copy(alpha = 0.78f),
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { onAddRepayment(debt.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ms_add),
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_repayment))
                        }
                    }
                }
                if (state.repayments.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.repayments),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    itemsIndexed(state.repayments, key = { _, p -> p.id }) { index, payment ->
                        val account = accounts.find { it.id == payment.accountId }?.name.orEmpty()
                        SegmentedListItem(
                            onClick = { onEditRepayment(debt.id, payment.id) },
                            shapes = ListItemDefaults.segmentedShapes(index, state.repayments.size),
                            colors = denaroSegmentedItemColors(),
                            modifier = Modifier.padding(
                                bottom = if (index < state.repayments.lastIndex) {
                                    ListItemDefaults.SegmentedGap
                                } else {
                                    0.dp
                                },
                            ),
                            leadingContent = {
                                DenaroIconBadge(
                                    icon = R.drawable.ms_payments,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    size = 40.dp,
                                    iconSize = 20.dp,
                                )
                            },
                            supportingContent = {
                                Text(
                                    listOf(
                                        account,
                                        payment.occurredAt.formattedDate(),
                                    ).filter(String::isNotBlank).joinToString(" · "),
                                )
                            },
                        ) {
                            Text(
                                text = if (amountsVisible) {
                                    Money.format(
                                        payment.amountMinor,
                                        debt.currency,
                                        debt.fractionDigits,
                                    )
                                } else {
                                    stringResource(R.string.amount_hidden)
                                },
                                style = MaterialTheme.typography.titleMediumEmphasized.tabularNumbers(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebtEditorScreen(
    repository: FinanceRepository,
    debtId: String?,
    initialDirection: DebtDirection = DebtDirection.BORROWED,
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    onDeleted: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val accounts by remember(repository) { repository.observeAllAccounts() }
        .collectAsStateWithLifecycle(emptyList())
    val counterparties by remember(repository) { repository.observeCounterparties(includeArchived = true) }
        .collectAsStateWithLifecycle(emptyList())
    var direction by rememberSaveable { mutableStateOf(initialDirection) }
    var counterpartyId by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var openedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var dueDate by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var hasRepayments by rememberSaveable { mutableStateOf(false) }
    var repaidAmountMinor by rememberSaveable { mutableLongStateOf(0L) }
    var loaded by rememberSaveable(debtId) { mutableStateOf(debtId == null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showNewCounterparty by remember { mutableStateOf(false) }
    var newCounterpartyInitialName by remember { mutableStateOf("") }
    var showOpenedPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var defaultsAppliedDirection by remember(debtId) {
        mutableStateOf<DebtDirection?>(null)
    }
    var accountDefaultsAppliedDirection by rememberSaveable(debtId) {
        mutableStateOf<DebtDirection?>(null)
    }
    var counterpartyDefaultsAppliedDirection by rememberSaveable(debtId) {
        mutableStateOf<DebtDirection?>(null)
    }
    var accountCustomized by rememberSaveable(debtId) { mutableStateOf(false) }
    var counterpartyCustomized by rememberSaveable(debtId) { mutableStateOf(false) }
    var debtDefaults by remember { mutableStateOf<DebtEntryDefaults?>(null) }
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.debt_saved)
    val deleted = stringResource(R.string.debt_deleted)
    val selectedAccount = accounts.firstOrNull { it.id == accountId }
    val selectorsReady = loaded
    val belowRepaymentsWarning = selectedAccount?.let { account ->
        runCatching { Money.parseMinorUnits(amount, account.fractionDigits) }.getOrNull()
            ?.let { parsed ->
                if (repaidAmountMinor > 0 && parsed < repaidAmountMinor) {
                    stringResource(
                        R.string.principal_below_repayments,
                        Money.format(
                            repaidAmountMinor,
                            account.currency,
                            account.fractionDigits,
                        ),
                    )
                } else {
                    null
                }
            }
    }

    LaunchedEffect(direction, debtId) {
        if (debtId == null && defaultsAppliedDirection != direction) {
            debtDefaults = repository.getDebtEntryDefaults(direction)
            defaultsAppliedDirection = direction
        }
    }
    LaunchedEffect(accounts, direction, defaultsAppliedDirection, debtId) {
        val defaults = debtDefaults
        if (
            debtId == null &&
            defaultsAppliedDirection == direction &&
            accountDefaultsAppliedDirection != direction &&
            accounts.isNotEmpty()
        ) {
            if (!accountCustomized) {
                accountId = defaults?.accountId?.takeIf { preferredId ->
                    accounts.any { it.id == preferredId && it.archivedAt == null }
                } ?: accounts.firstOrNull { it.archivedAt == null }?.id.orEmpty()
            }
            accountDefaultsAppliedDirection = direction
        }
    }
    LaunchedEffect(counterparties, direction, defaultsAppliedDirection, debtId) {
        val defaults = debtDefaults
        if (
            debtId == null &&
            defaultsAppliedDirection == direction &&
            counterpartyDefaultsAppliedDirection != direction &&
            counterparties.isNotEmpty()
        ) {
            if (!counterpartyCustomized) {
                counterpartyId = defaults?.counterpartyId?.takeIf { preferredId ->
                    counterparties.any { it.id == preferredId && it.archivedAt == null }
                } ?: counterparties.firstOrNull { it.archivedAt == null }?.id.orEmpty()
            }
            counterpartyDefaultsAppliedDirection = direction
        }
    }
    LaunchedEffect(debtId) {
        debtId?.let { id ->
            repository.observeDebt(id).first { it != null }?.let { debt ->
                direction = debt.direction
                counterpartyId = debt.counterpartyId
                accountId = debt.accountId
                amount = Money.toInputAmount(debt.principalMinor, debt.fractionDigits)
                openedAt = debt.openedAt
                dueDate = debt.dueDate
                note = debt.note.orEmpty()
                hasRepayments = debt.repaidMinor > 0
                repaidAmountMinor = debt.repaidMinor
                loaded = true
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (debtId == null) R.string.add_debt else R.string.edit_debt))
                },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
                actions = {
                    FilledTonalButton(
                        enabled = loaded && selectedAccount != null && !saving,
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = {
                            val account = selectedAccount ?: return@FilledTonalButton
                            saving = true
                            scope.launch {
                                runCatching {
                                    val input = DebtInput(
                                        counterpartyId,
                                        accountId,
                                        direction,
                                        Money.parseMinorUnits(amount, account.fractionDigits),
                                        openedAt,
                                        dueDate,
                                        note,
                                    )
                                    if (debtId == null) {
                                        repository.createDebt(input)
                                    } else {
                                        repository.updateDebt(debtId, input)
                                        debtId
                                    }
                                }.onSuccess { onMessage(saved); onFinished(it) }
                                    .onFailure { error = it.message; saving = false }
                            }
                        },
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DenaroSegmentedChoiceRow(
                options = DebtDirection.entries,
                selectedOption = direction,
                enabled = !hasRepayments,
                onSelect = { option ->
                    direction = option
                    defaultsAppliedDirection = null
                    accountDefaultsAppliedDirection = null
                    counterpartyDefaultsAppliedDirection = null
                    accountCustomized = false
                    counterpartyCustomized = false
                },
                label = { option ->
                    stringResource(
                        if (option == DebtDirection.BORROWED) R.string.borrowed else R.string.lent,
                    )
                },
            )
            DenaroSectionCard(contentPadding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val selectableCounterparties = remember(counterparties, counterpartyId) {
                        counterparties.filter { it.archivedAt == null || it.id == counterpartyId }
                    }
                    AdaptiveSelector(
                        label = stringResource(R.string.counterparty),
                        selectedText = counterparties.find { it.id == counterpartyId }?.name.orEmpty(),
                        options = selectableCounterparties.map { party ->
                            SelectorOption(
                                id = party.id,
                                value = party.id,
                                label = party.name,
                                supportingText = party.note?.takeIf(String::isNotBlank),
                                selected = party.id == counterpartyId,
                            )
                        },
                        onSelected = {
                            counterpartyId = it
                            counterpartyCustomized = true
                        },
                        enabled = selectorsReady,
                        footerAction = SelectorAction(
                            label = stringResource(R.string.add_counterparty),
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ms_add),
                                    contentDescription = null,
                                )
                            },
                            onClick = { query ->
                                newCounterpartyInitialName = query
                                showNewCounterparty = true
                            },
                        ),
                    )
                    AccountSelector(
                        label = stringResource(
                            if (direction == DebtDirection.BORROWED) {
                                R.string.received_into
                            } else {
                                R.string.paid_from
                            },
                        ),
                        accounts = accounts.filter { it.archivedAt == null || it.id == accountId },
                        selectedId = accountId,
                        onSelected = {
                            amount = Money.rescaleInput(
                                amount,
                                accounts.firstOrNull { account -> account.id == accountId }?.fractionDigits,
                                accounts.firstOrNull { account -> account.id == it }?.fractionDigits,
                            )
                            accountId = it
                            accountCustomized = true
                        },
                        enabled = selectorsReady,
                    )
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.large,
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(stringResource(R.string.amount)) },
                        suffix = selectedAccount?.let { { Text(it.currency) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    belowRepaymentsWarning?.takeIf { !saving }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.denaroColors.warning,
                        )
                    }
                }
            }
            DenaroSectionCard(contentPadding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(
                        label = R.string.opened_date,
                        value = openedAt.formattedDate(),
                        onClick = { showOpenedPicker = true },
                    )
                    DateField(
                        label = R.string.due_date,
                        value = dueDate?.let { LocalDate.parse(it).formattedDate() }.orEmpty(),
                        onClick = { showDuePicker = true },
                        onClear = if (dueDate != null) ({ dueDate = null }) else null,
                    )
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.large,
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.description)) },
                        supportingText = { Text(stringResource(R.string.optional)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (debtId != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            }
        }
    }
    if (showNewCounterparty) {
        CounterpartyDialog(
            existing = null,
            initialName = newCounterpartyInitialName,
            onDismiss = {
                showNewCounterparty = false
                newCounterpartyInitialName = ""
            },
        ) { input ->
            scope.launch {
                runCatching { repository.createCounterparty(input) }.onSuccess {
                    counterpartyId = it
                    counterpartyCustomized = true
                    showNewCounterparty = false
                    newCounterpartyInitialName = ""
                }.onFailure { error = it.message }
            }
        }
    }
    if (showOpenedPicker) {
        LocalDatePicker(openedAt, { showOpenedPicker = false }) {
            openedAt = it
            showOpenedPicker = false
        }
    }
    if (showDuePicker) {
        LocalDatePicker(
            initial = dueDate?.let {
                LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } ?: openedAt,
            onDismiss = { showDuePicker = false },
        ) {
            dueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
            showDuePicker = false
        }
    }
    if (confirmDelete && debtId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_debt)) },
            text = { Text(stringResource(R.string.delete_debt_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteDebt(debtId)
                            onMessage(deleted)
                            onDeleted()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun DebtRepaymentEditorScreen(
    repository: FinanceRepository,
    debtId: String,
    repaymentId: String?,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val debt by remember(repository, debtId) { repository.observeDebt(debtId) }
        .collectAsStateWithLifecycle(null)
    val accounts by remember(repository) { repository.observeActiveAccounts() }
        .collectAsStateWithLifecycle(emptyList())
    val allAccounts by remember(repository) { repository.observeAllAccounts() }
        .collectAsStateWithLifecycle(emptyList())
    var accountId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var originalAmountMinor by rememberSaveable { mutableLongStateOf(0L) }
    var occurredAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var note by rememberSaveable { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var loaded by rememberSaveable(debtId, repaymentId) {
        mutableStateOf(repaymentId == null)
    }
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.repayment_saved)
    val selectedAccount = accounts.firstOrNull { it.id == accountId }
    val requiredDataLoaded = loaded && debt != null && selectedAccount != null
    val overOutstandingWarning = debt?.let { currentDebt ->
        runCatching { Money.parseMinorUnits(amount, currentDebt.fractionDigits) }.getOrNull()
            ?.let { parsed ->
                val baseOutstanding = currentDebt.principalMinor -
                        (currentDebt.repaidMinor - if (repaymentId == null) 0L else originalAmountMinor)
                if (parsed > baseOutstanding) {
                    stringResource(
                        R.string.repayment_over_outstanding,
                        Money.format(
                            maxOf(0L, baseOutstanding),
                            currentDebt.currency,
                            currentDebt.fractionDigits,
                        ),
                    )
                } else {
                    null
                }
            }
    }
    LaunchedEffect(debt, accounts, repaymentId) {
        if (repaymentId == null && accountId.isBlank()) {
            accountId = accounts.firstOrNull { it.currency == debt?.currency }?.id.orEmpty()
        }
    }
    LaunchedEffect(repaymentId, debt, allAccounts) {
        val existingRepaymentId = repaymentId ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        val currentDebt = debt ?: return@LaunchedEffect
        val repayment = repository.getDebtRepayment(existingRepaymentId) ?: return@LaunchedEffect
        val account = allAccounts.firstOrNull { it.id == repayment.accountId }
        if (account == null || account.currency != currentDebt.currency) return@LaunchedEffect
        accountId = repayment.accountId
        amount = Money.toInputAmount(repayment.amountMinor, currentDebt.fractionDigits)
        originalAmountMinor = repayment.amountMinor
        occurredAt = repayment.occurredAt
        note = repayment.note.orEmpty()
        loaded = true
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (repaymentId == null) R.string.add_repayment else R.string.edit_repayment,
                        ),
                    )
                },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
                actions = {
                    FilledTonalButton(
                        enabled = requiredDataLoaded && !saving,
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = {
                            val account = selectedAccount ?: return@FilledTonalButton
                            if (debt == null) return@FilledTonalButton
                            saving = true
                            scope.launch {
                                runCatching {
                                    val input = DebtRepaymentInput(
                                        debtId,
                                        accountId,
                                        Money.parseMinorUnits(amount, account.fractionDigits),
                                        occurredAt,
                                        note,
                                    )
                                    if (repaymentId == null) {
                                        repository.createDebtRepayment(input)
                                    } else {
                                        repository.updateDebtRepayment(repaymentId, input)
                                    }
                                }.onSuccess { onMessage(saved); onFinished() }
                                    .onFailure { error = it.message; saving = false }
                            }
                        },
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DenaroSectionCard(contentPadding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccountSelector(
                        stringResource(R.string.account),
                        accounts.filter { it.currency == debt?.currency },
                        accountId,
                        {
                            amount = Money.rescaleInput(
                                amount,
                                accounts.firstOrNull { account -> account.id == accountId }?.fractionDigits,
                                accounts.firstOrNull { account -> account.id == it }?.fractionDigits,
                            )
                            accountId = it
                        },
                        enabled = loaded && debt != null,
                    )
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.large,
                        value = amount,
                        onValueChange = { amount = it },
                        enabled = loaded,
                        label = { Text(stringResource(R.string.amount)) },
                        suffix = selectedAccount?.let { { Text(it.currency) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    overOutstandingWarning?.takeIf { !saving }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.denaroColors.warning,
                        )
                    }
                    DateField(
                        label = R.string.date,
                        value = occurredAt.formattedDate(),
                        onClick = { showDate = true },
                    )
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.large,
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.description)) },
                        supportingText = { Text(stringResource(R.string.optional)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (repaymentId != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            }
        }
    }
    if (showDate) {
        LocalDatePicker(occurredAt, { showDate = false }) {
            occurredAt = it
            showDate = false
        }
    }
    if (confirmDelete && repaymentId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_repayment)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteDebtRepayment(repaymentId)
                            onFinished()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun CounterpartiesScreen(repository: FinanceRepository, onBack: () -> Unit) {
    val parties by remember(repository) { repository.observeCounterparties(includeArchived = true) }
        .collectAsStateWithLifecycle(emptyList())
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by remember { mutableStateOf<CounterpartySummary?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CounterpartySummary?>(null) }
    var deletingInProgress by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val showSearch = shouldUseSearchableSelector(parties.size)
    LaunchedEffect(showSearch) {
        if (!showSearch) searchQuery = ""
    }
    val visibleParties = remember(parties, searchQuery, showSearch) {
        if (!showSearch || searchQuery.isBlank()) {
            parties
        } else {
            parties.filter { party ->
                matchesSelectorSearch(searchQuery, listOfNotNull(party.name, party.note))
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleteFailedMessage = stringResource(R.string.counterparty_delete_failed)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.counterparties)) },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painterResource(R.drawable.ms_add),
                    contentDescription = stringResource(R.string.add_counterparty),
                )
            }
        },
    ) { padding ->
        if (parties.isEmpty()) {
            EmptyState(
                icon = R.drawable.ms_group,
                title = stringResource(R.string.no_counterparties),
                description = stringResource(R.string.manage_counterparties),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                action = {
                    Button(onClick = { adding = true }) {
                        Text(stringResource(R.string.add_counterparty))
                    }
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (showSearch) {
                    InlineSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (visibleParties.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_matching_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 104.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        itemsIndexed(
                            visibleParties,
                            key = { _, party -> party.id }) { index, party ->
                            SegmentedListItem(
                                onClick = { editing = party },
                                shapes = ListItemDefaults.segmentedShapes(
                                    index,
                                    visibleParties.size
                                ),
                                colors = denaroSegmentedItemColors(),
                                leadingContent = {
                                    DenaroIconBadge(
                                        icon = R.drawable.ms_group,
                                        containerColor = if (party.archivedAt == null) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        contentColor = if (party.archivedAt == null) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        size = 40.dp,
                                        iconSize = 20.dp,
                                    )
                                },
                                supportingContent = party.note?.takeIf(String::isNotBlank)?.let {
                                    { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (party.debtCount == 0) {
                                            IconButton(onClick = { deleting = party }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ms_delete),
                                                    contentDescription = stringResource(R.string.delete_counterparty_action),
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    repository.setCounterpartyArchived(
                                                        party.id,
                                                        party.archivedAt == null,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(
                                                    if (party.archivedAt == null) {
                                                        R.drawable.ms_archive
                                                    } else {
                                                        R.drawable.ms_unarchive
                                                    },
                                                ),
                                                contentDescription = stringResource(
                                                    if (party.archivedAt == null) R.string.archive else R.string.restore,
                                                ),
                                            )
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    text = party.name,
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        CounterpartyDialog(
            existing = null,
            initialName = searchQuery.trim(),
            onDismiss = { adding = false },
        ) {
            scope.launch {
                repository.createCounterparty(it)
                adding = false
            }
        }
    }
    editing?.let { party ->
        CounterpartyDialog(
            existing = party,
            onDismiss = { editing = null },
        ) {
            scope.launch {
                repository.updateCounterparty(party.id, it)
                editing = null
            }
        }
    }
    deleting?.let { party ->
        AlertDialog(
            onDismissRequest = { if (!deletingInProgress) deleting = null },
            title = { Text(stringResource(R.string.delete_counterparty)) },
            text = {
                Text(stringResource(R.string.delete_counterparty_message, party.name))
            },
            confirmButton = {
                TextButton(
                    enabled = !deletingInProgress,
                    onClick = {
                        deletingInProgress = true
                        scope.launch {
                            runCatching { repository.deleteCounterparty(party.id) }
                                .onSuccess { deleting = null }
                                .onFailure {
                                    deleting = null
                                    snackbarHostState.showSnackbar(deleteFailedMessage)
                                }
                            deletingInProgress = false
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deletingInProgress,
                    onClick = { deleting = null },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CounterpartyDialog(
    existing: CounterpartySummary?,
    onDismiss: () -> Unit,
    initialName: String = "",
    onSave: (CounterpartyInput) -> Unit,
) {
    var name by remember(existing, initialName) {
        mutableStateOf(existing?.name ?: initialName)
    }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.add_counterparty else R.string.edit_counterparty,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.large,
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    shape = MaterialTheme.shapes.large,
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.description)) },
                    supportingText = { Text(stringResource(R.string.optional)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(CounterpartyInput(name, note)) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DateField(
    label: Int,
    value: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ms_calendar_month),
            contentDescription = null,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${stringResource(label)}: ${value.ifBlank { stringResource(R.string.none) }}",
            modifier = Modifier.weight(1f),
        )
        onClear?.let { clear ->
            IconButton(onClick = clear) {
                Icon(
                    painter = painterResource(R.drawable.ms_close),
                    contentDescription = stringResource(R.string.clear_due_date),
                )
            }
        }
    }
}

@Composable
private fun LocalDatePicker(initial: Long, onDismiss: () -> Unit, onSelected: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.utcDateMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onSelected(it.localNoonMillis()) } }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state)
    }
}

private fun Long.utcDateMillis(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.localNoonMillis(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun LocalDate.formattedDate(): String =
    atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().formattedDate()
