@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.rfmariano.denaro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.export.DEFAULT_STATEMENT_COLUMNS
import it.rfmariano.denaro.data.export.StatementColumn
import it.rfmariano.denaro.data.export.StatementDateRange
import it.rfmariano.denaro.data.export.StatementLabels
import it.rfmariano.denaro.data.export.StatementLayout
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import com.composables.icons.lucide.R as LucideR

@Composable
internal fun ExportStatementScreen(
    repository: FinanceRepository,
    preferencesRepository: AppPreferencesRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ExportStatementViewModel = viewModel(
        factory = viewModelFactory {
            ExportStatementViewModel(
                repository = repository,
                preferencesRepository = preferencesRepository,
                contentResolver = context.applicationContext.contentResolver,
            )
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val labels = remember(context) { statementLabels(context) }
    val exportedMessage = stringResource(R.string.statement_export_success)
    val failedMessage = stringResource(R.string.statement_export_failed)

    LaunchedEffect(uiState.result) {
        when (uiState.result) {
            ExportStatementResult.SUCCESS -> snackbarHostState.showSnackbar(exportedMessage)
            ExportStatementResult.FAILED -> snackbarHostState.showSnackbar(failedMessage)
            null -> Unit
        }
        viewModel.consumeResult()
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MIME_TYPE),
    ) { uri ->
        if (uri != null) viewModel.export(uri, labels)
    }

    var editingFrom by rememberSaveable { mutableStateOf(false) }
    var editingTo by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_statement)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader(
                label = stringResource(R.string.account),
                allSelected = uiState.accounts.isNotEmpty() &&
                        uiState.accounts.all { it.id in uiState.selectedAccountIds },
                selectAllDescription = stringResource(R.string.statement_all_accounts),
                onToggleAll = {
                    viewModel.selectAllAccounts(
                        uiState.accounts.isEmpty() ||
                                uiState.accounts.any { it.id !in uiState.selectedAccountIds })
                },
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            AccountSelector(
                accounts = uiState.accounts,
                selectedAccountIds = uiState.selectedAccountIds,
                onToggle = viewModel::toggleAccount,
            )
            DateRangeDropdown(
                range = uiState.dateRange,
                onSelect = viewModel::selectDateRange,
            )
            if (uiState.dateRange == StatementDateRange.CUSTOM) {
                DateField(
                    label = stringResource(R.string.statement_from),
                    value = uiState.customFromDate,
                    onClick = { editingFrom = true },
                    modifier = Modifier.padding(top = 8.dp),
                )
                DateField(
                    label = stringResource(R.string.statement_to),
                    value = uiState.customToDate,
                    onClick = { editingTo = true },
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (invalidCustomRange(uiState)) {
                    Text(
                        text = stringResource(R.string.statement_invalid_date_range),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                text = stringResource(R.string.statement_layout),
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LayoutSelector(
                layout = uiState.layout,
                onSelect = viewModel::selectLayout,
            )
            SectionHeader(
                label = stringResource(R.string.statement_columns),
                allSelected = DEFAULT_STATEMENT_COLUMNS.all { it in uiState.columns } &&
                        (uiState.selectedAccountIds.size <= 1 || uiState.accountColumnEnabled),
                selectAllDescription = stringResource(R.string.statement_all_columns),
                onToggleAll = {
                    viewModel.setAllColumns(!(DEFAULT_STATEMENT_COLUMNS.all { it in uiState.columns } &&
                            (uiState.selectedAccountIds.size <= 1 || uiState.accountColumnEnabled)))
                },
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            ColumnSelector(
                accountColumnVisible = uiState.selectedAccountIds.size > 1,
                accountColumnEnabled = uiState.accountColumnEnabled,
                columns = uiState.columns,
                onAccountColumnChange = viewModel::setAccountColumn,
                onToggleColumn = viewModel::toggleColumn,
            )
            val hasMoneyColumn = StatementColumn.CREDIT in uiState.columns ||
                    StatementColumn.DEBIT in uiState.columns
            if (!hasMoneyColumn) {
                Text(
                    text = stringResource(R.string.statement_need_money_column),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { createDocument.launch(defaultStatementName()) },
                enabled = uiState.selectedAccountIds.isNotEmpty() &&
                        !uiState.exporting &&
                        hasMoneyColumn &&
                        !invalidCustomRange(uiState),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 24.dp),
            ) {
                Text(stringResource(R.string.statement_export))
            }
        }
    }

    if (editingFrom) {
        LocalDatePicker(
            initial = uiState.customFromDate,
            onDismiss = { editingFrom = false },
            onSelected = {
                viewModel.selectCustomDates(it, uiState.customToDate)
                editingFrom = false
            },
        )
    }
    if (editingTo) {
        LocalDatePicker(
            initial = uiState.customToDate,
            onDismiss = { editingTo = false },
            onSelected = {
                viewModel.selectCustomDates(uiState.customFromDate, it)
                editingTo = false
            },
        )
    }
}

@Composable
private fun AccountSelector(
    accounts: List<AccountSummary>,
    selectedAccountIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    ChipFlow {
        accounts.forEach { account ->
            FilterChip(
                selected = account.id in selectedAccountIds,
                onClick = { onToggle(account.id, account.id !in selectedAccountIds) },
                label = { Text("${account.name} · ${account.currency}") },
            )
        }
    }
}

@Composable
private fun ColumnSelector(
    accountColumnVisible: Boolean,
    accountColumnEnabled: Boolean,
    columns: Set<StatementColumn>,
    onAccountColumnChange: (Boolean) -> Unit,
    onToggleColumn: (StatementColumn, Boolean) -> Unit,
) {
    val standardColumns = StatementColumn.entries.filter { it != StatementColumn.ACCOUNT }
    ChipFlow {
        if (accountColumnVisible) {
            FilterChip(
                selected = accountColumnEnabled,
                onClick = { onAccountColumnChange(!accountColumnEnabled) },
                label = { Text(stringResource(R.string.account)) },
            )
        }
        standardColumns.forEach { column ->
            FilterChip(
                selected = column in columns,
                onClick = { onToggleColumn(column, column !in columns) },
                label = { Text(stringResource(column.labelRes())) },
            )
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    allSelected: Boolean,
    selectAllDescription: String,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        IconToggleButton(checked = allSelected, onCheckedChange = { onToggleAll() }) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_list_checks),
                contentDescription = selectAllDescription,
            )
        }
    }
}

@Composable
private fun ChipFlow(chips: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = chips,
    )
}

@Composable
private fun LayoutSelector(
    layout: StatementLayout,
    onSelect: (StatementLayout) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        StatementLayout.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = layout == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, StatementLayout.entries.size),
            ) {
                Text(stringResource(option.labelRes()))
            }
        }
    }
}

@Composable
private fun DateRangeDropdown(
    range: StatementDateRange,
    onSelect: (StatementDateRange) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        OutlinedTextField(
            value = stringResource(range.labelRes()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.statement_date_range)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            StatementDateRange.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, end = 8.dp),
    ) {
        Text(
            text = "$label: ${value?.format(MEDIUM_DATE) ?: stringResource(R.string.none)}",
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_calendar_days),
            contentDescription = label,
        )
    }
}

@Composable
private fun LocalDatePicker(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toUtcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onSelected(it.utcToLocalDate()) }
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun StatementColumn.labelRes(): Int = when (this) {
    StatementColumn.ACCOUNT -> R.string.account
    StatementColumn.DATE -> R.string.date
    StatementColumn.DESCRIPTION -> R.string.description
    StatementColumn.CATEGORY -> R.string.category
    StatementColumn.CREDIT -> R.string.statement_column_credit
    StatementColumn.DEBIT -> R.string.statement_column_debit
    StatementColumn.BALANCE -> R.string.statement_column_balance
}

private fun StatementLayout.labelRes(): Int = when (this) {
    StatementLayout.GROUPED -> R.string.statement_layout_grouped
    StatementLayout.CHRONOLOGICAL -> R.string.statement_layout_chronological
}

private fun StatementDateRange.labelRes(): Int = when (this) {
    StatementDateRange.THIS_MONTH -> R.string.statement_range_this_month
    StatementDateRange.LAST_MONTH -> R.string.statement_range_last_month
    StatementDateRange.THIS_YEAR -> R.string.statement_range_this_year
    StatementDateRange.LAST_YEAR -> R.string.statement_range_last_year
    StatementDateRange.ALL_TIME -> R.string.statement_range_all_time
    StatementDateRange.CUSTOM -> R.string.statement_range_custom
}

private fun statementLabels(context: android.content.Context): StatementLabels = StatementLabels(
    columnAccount = context.getString(R.string.account),
    columnDate = context.getString(R.string.date),
    columnDescription = context.getString(R.string.description),
    columnCategory = context.getString(R.string.category),
    columnCredit = context.getString(R.string.statement_column_credit),
    columnDebit = context.getString(R.string.statement_column_debit),
    columnBalance = context.getString(R.string.statement_column_balance),
    transferFrom = { context.getString(R.string.statement_transfer_from, it) },
    transferTo = { context.getString(R.string.statement_transfer_to, it) },
    balanceAdjustment = context.getString(R.string.balance_adjustment),
    borrowedFrom = { context.getString(R.string.borrowed_from, it) },
    lentTo = { context.getString(R.string.lent_to, it) },
    repaidTo = { context.getString(R.string.repaid_to, it) },
    repaidBy = { context.getString(R.string.repaid_by, it) },
)

private fun defaultStatementName(): String =
    "denaro-statement-${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())}.csv"

private fun invalidCustomRange(state: ExportStatementUiState): Boolean =
    state.dateRange == StatementDateRange.CUSTOM &&
            state.customFromDate != null &&
            state.customToDate != null &&
            state.customFromDate > state.customToDate

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcToLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private val MEDIUM_DATE: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private const val CSV_MIME_TYPE = "text/csv"
