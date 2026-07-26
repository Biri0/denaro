@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountInput
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.finance.SupportedCurrencies
import it.rfmariano.denaro.data.finance.TransferAccountSuggestions
import it.rfmariano.denaro.data.finance.TransactionInput
import it.rfmariano.denaro.data.finance.TransferInput
import it.rfmariano.denaro.data.local.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AccountEditorScreen(
    repository: FinanceRepository,
    accountId: String?,
    onFinished: (String) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var openingBalance by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("EUR") }
    var loaded by rememberSaveable(accountId) { mutableStateOf(accountId == null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.account_saved)
    val archivedMessage = stringResource(R.string.account_archived)

    LaunchedEffect(accountId) {
        if (accountId != null) {
            repository.observeAccount(accountId).first { it != null }?.let { account ->
                name = account.name
                description = account.description.orEmpty()
                openingBalance = account.openingBalanceMinor.toInputAmount()
                currency = account.currency
                loaded = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (accountId == null) {
                                R.string.add_account
                            } else {
                                R.string.edit_account
                            },
                        ),
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(
                        enabled = loaded && !isSaving,
                        onClick = {
                            if (isSaving) return@TextButton
                            isSaving = true
                            error = null
                            scope.launch {
                                runCatching {
                                    val input = AccountInput(
                                        name = name,
                                        description = description,
                                        openingBalanceMinor = Money.parseMinorUnits(
                                            openingBalance.ifBlank { "0.00" },
                                            allowNegative = true,
                                        ),
                                        currency = currency,
                                    )
                                    if (accountId == null) {
                                        repository.createAccount(input)
                                    } else {
                                        repository.updateAccount(accountId, input)
                                        accountId
                                    }
                                }.onSuccess {
                                    onMessage(savedMessage)
                                    onFinished(it)
                                }.onFailure {
                                    error = it.message
                                    isSaving = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                supportingText = { Text(stringResource(R.string.optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = openingBalance,
                onValueChange = { openingBalance = it },
                label = { Text(stringResource(R.string.opening_balance)) },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                suffix = { Text(currency) },
                supportingText = {
                    Text(stringResource(R.string.opening_balance_warning))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenuBox(
                expanded = showCurrencyMenu,
                onExpandedChange = {
                    if (accountId == null) showCurrencyMenu = !showCurrencyMenu
                },
            ) {
                OutlinedTextField(
                    value = currency,
                    onValueChange = {},
                    readOnly = true,
                    enabled = accountId == null,
                    label = { Text(stringResource(R.string.currency)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(showCurrencyMenu)
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(
                        ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = accountId == null,
                    ),
                )
                ExposedDropdownMenu(
                    expanded = showCurrencyMenu,
                    onDismissRequest = { showCurrencyMenu = false },
                ) {
                    SupportedCurrencies.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code) },
                            onClick = {
                                currency = code
                                showCurrencyMenu = false
                            },
                        )
                    }
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (accountId != null) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = { confirmArchive = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.archive))
                }
            }
        }
    }

    if (confirmArchive && accountId != null) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.archive_account)) },
            text = { Text(stringResource(R.string.archive_account_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmArchive = false
                        scope.launch {
                            runCatching { repository.archiveAccount(accountId) }
                                .onSuccess {
                                    onMessage(archivedMessage)
                                    onFinished(accountId)
                                }
                                .onFailure { error = it.message }
                        }
                    },
                ) {
                    Text(stringResource(R.string.archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun ActivityEditorScreen(
    repository: FinanceRepository,
    route: ActivityEditorRoute,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val accounts by repository.observeActiveAccounts().collectAsStateWithLifecycle(emptyList())
    var kind by rememberSaveable { mutableStateOf(route.kind) }
    var accountId by rememberSaveable { mutableStateOf("") }
    var destinationId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var occurredAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var recurringRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var loaded by rememberSaveable(route.id) { mutableStateOf(route.id == null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var transferSuggestions by remember { mutableStateOf(TransferAccountSuggestions()) }
    var transferSuggestionsLoaded by remember { mutableStateOf(false) }
    var transferDefaultsApplied by rememberSaveable(route.id) { mutableStateOf(false) }
    var transferSourceCustomized by rememberSaveable(route.id) { mutableStateOf(false) }
    var transferDestinationCustomized by rememberSaveable(route.id) {
        mutableStateOf(false)
    }
    var isSaving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.activity_saved)
    val deletedMessage = stringResource(R.string.activity_deleted)

    LaunchedEffect(accounts, route.id, kind) {
        if (
            route.id == null &&
            kind != ActivityKind.TRANSFER &&
            accountId.isEmpty() &&
            accounts.isNotEmpty()
        ) {
            accountId = accounts.first().id
        }
    }
    LaunchedEffect(accounts, route.id) {
        if (route.id == null && accounts.isNotEmpty()) {
            transferSuggestions = repository.getTransferAccountSuggestions()
            transferSuggestionsLoaded = true
        }
    }
    LaunchedEffect(
        accounts,
        kind,
        route.id,
        transferSuggestionsLoaded,
        transferDefaultsApplied,
    ) {
        if (
            route.id == null &&
            kind == ActivityKind.TRANSFER &&
            accounts.isNotEmpty() &&
            transferSuggestionsLoaded &&
            !transferDefaultsApplied
        ) {
            val sourceId = if (transferSourceCustomized) {
                accountId
            } else {
                transferSuggestions.preferredSourceId
                    ?: accountId.takeIf { selected ->
                        accounts.any { it.id == selected }
                    }
                    ?: accounts.first().id
            }
            accountId = sourceId
            if (!transferDestinationCustomized) {
                destinationId = suggestedDestinationId(
                    sourceId = sourceId,
                    accounts = accounts,
                    suggestions = transferSuggestions,
                )
            }
            transferDefaultsApplied = true
        }
    }
    LaunchedEffect(route.id) {
        route.id?.let { id ->
            if (route.kind == ActivityKind.TRANSFER) {
                repository.getTransfer(id)?.let {
                    accountId = it.fromAccountId
                    destinationId = it.toAccountId
                    amount = it.amountMinor.toInputAmount()
                    description = it.description.orEmpty()
                    occurredAt = it.occurredAt
                }
            } else {
                repository.getTransaction(id)?.let {
                    kind = if (it.type == TransactionType.INCOME) {
                        ActivityKind.INCOME
                    } else {
                        ActivityKind.EXPENSE
                    }
                    accountId = it.accountId
                    amount = it.amountMinor.toInputAmount()
                    description = it.description.orEmpty()
                    occurredAt = it.occurredAt
                    recurringRuleId = it.recurringRuleId
                }
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                route.id == null -> R.string.add_transaction
                                route.kind == ActivityKind.TRANSFER -> R.string.edit_transfer
                                else -> R.string.edit_transaction
                            },
                        ),
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(
                        enabled = loaded && !isSaving,
                        onClick = {
                            if (isSaving) return@TextButton
                            isSaving = true
                            error = null
                            scope.launch {
                                runCatching {
                                    val amountMinor = Money.parseMinorUnits(amount)
                                    if (kind == ActivityKind.TRANSFER) {
                                        val input = TransferInput(
                                            fromAccountId = accountId,
                                            toAccountId = destinationId,
                                            amountMinor = amountMinor,
                                            occurredAt = occurredAt,
                                            description = description,
                                        )
                                        if (route.id == null) {
                                            repository.createTransfer(input)
                                        } else {
                                            repository.updateTransfer(route.id, input)
                                        }
                                    } else {
                                        val input = TransactionInput(
                                            accountId = accountId,
                                            amountMinor = amountMinor,
                                            type = if (kind == ActivityKind.INCOME) {
                                                TransactionType.INCOME
                                            } else {
                                                TransactionType.EXPENSE
                                            },
                                            occurredAt = occurredAt,
                                            description = description,
                                        )
                                        if (route.id == null) {
                                            repository.createTransaction(input)
                                        } else {
                                            repository.updateTransaction(route.id, input)
                                        }
                                    }
                                }.onSuccess {
                                    onMessage(savedMessage)
                                    onFinished()
                                }.onFailure {
                                    error = it.message
                                    isSaving = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ActivityKind.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = kind == option,
                        enabled = route.id == null,
                        onClick = {
                            if (option == ActivityKind.TRANSFER && kind != option) {
                                transferDefaultsApplied = false
                                transferSourceCustomized = false
                                transferDestinationCustomized = false
                            }
                            kind = option
                            destinationId = ""
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            ActivityKind.entries.size,
                        ),
                    ) {
                        Text(
                            stringResource(
                                when (option) {
                                    ActivityKind.INCOME -> R.string.income
                                    ActivityKind.EXPENSE -> R.string.expense
                                    ActivityKind.TRANSFER -> R.string.transfer
                                },
                            ),
                        )
                    }
                }
            }
            AccountSelector(
                label = if (kind == ActivityKind.TRANSFER) {
                    stringResource(R.string.from_account)
                } else {
                    stringResource(R.string.account)
                },
                accounts = accounts,
                selectedId = accountId,
                onSelected = {
                    accountId = it
                    if (kind == ActivityKind.TRANSFER) {
                        transferSourceCustomized = true
                        transferDestinationCustomized = false
                        destinationId = suggestedDestinationId(
                            sourceId = it,
                            accounts = accounts,
                            suggestions = transferSuggestions,
                        )
                    } else if (destinationId == it) {
                        destinationId = ""
                    }
                },
            )
            if (kind == ActivityKind.TRANSFER) {
                AccountSelector(
                    label = stringResource(R.string.to_account),
                    accounts = accounts.filter {
                        it.id != accountId &&
                            it.currency == accounts.find { source ->
                                source.id == accountId
                            }?.currency
                    },
                    selectedId = destinationId,
                    onSelected = {
                        transferDestinationCustomized = true
                        destinationId = it
                    },
                )
                Text(
                    stringResource(R.string.choose_same_currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${stringResource(R.string.date)}: ${occurredAt.formattedDate()}")
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                supportingText = { Text(stringResource(R.string.optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            recurringRuleId?.let {
                Text(
                    stringResource(R.string.recurring_occurrence_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (route.id != null) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = occurredAt.toUtcDateMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            occurredAt = it.withTimeFrom(occurredAt)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (confirmDelete && route.id != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_activity)) },
            text = { Text(stringResource(R.string.delete_activity_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            runCatching {
                                if (route.kind == ActivityKind.TRANSFER) {
                                    repository.deleteTransfer(route.id)
                                } else {
                                    repository.deleteTransaction(route.id)
                                }
                            }.onSuccess {
                                onMessage(deletedMessage)
                                onFinished()
                            }.onFailure { error = it.message }
                        }
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun suggestedDestinationId(
    sourceId: String,
    accounts: List<AccountSummary>,
    suggestions: TransferAccountSuggestions,
): String {
    suggestions.preferredDestinationIds[sourceId]?.let { return it }
    val sourceCurrency = accounts.find { it.id == sourceId }?.currency
    return accounts.singleOrNull {
        it.id != sourceId && it.currency == sourceCurrency
    }?.id.orEmpty()
}

@Composable
fun AccountSelector(
    label: String,
    accounts: List<AccountSummary>,
    selectedId: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.find { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.select_account)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled = true,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${account.name} · ${account.currency}") },
                    onClick = {
                        onSelected(account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun Long.toInputAmount(): String =
    BigDecimal.valueOf(this, 2).setScale(2).toPlainString()

private fun Long.toUtcDateMillis(): Long {
    val date = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun Long.withTimeFrom(existing: Long): Long {
    val date = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val time = Instant.ofEpochMilli(existing).atZone(ZoneId.systemDefault()).toLocalTime()
    return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
