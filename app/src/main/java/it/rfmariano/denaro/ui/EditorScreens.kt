@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountInput
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.ActivityKind
import it.rfmariano.denaro.data.finance.CategorySummary
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.finance.RecurringRuleInput
import it.rfmariano.denaro.data.finance.SupportedCurrencies
import it.rfmariano.denaro.data.finance.TransactionInput
import it.rfmariano.denaro.data.finance.TransferAccountSuggestions
import it.rfmariano.denaro.data.finance.TransferInput
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import com.composables.icons.lucide.R as LucideR

@Composable
fun AccountEditorScreen(
    repository: FinanceRepository,
    defaultCurrency: String = "EUR",
    accountId: String?,
    onFinished: (String) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var openingBalance by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable(accountId) { mutableStateOf(defaultCurrency) }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
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
    onEditSchedule: (String) -> Unit = {},
    createdCategoryId: String? = null,
    onCreatedCategoryConsumed: () -> Unit = {},
    onAddCategory: (TransactionType) -> Unit = {},
) {
    val accounts by repository.observeActiveAccounts().collectAsStateWithLifecycle(emptyList())
    val categories by repository.observeCategories(includeArchived = true)
        .collectAsStateWithLifecycle(emptyList())
    var kind by rememberSaveable { mutableStateOf(route.kind) }
    var accountId by rememberSaveable { mutableStateOf(route.accountId.orEmpty()) }
    var destinationId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var occurredAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var recurringRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var isScheduled by rememberSaveable {
        mutableStateOf(route.scheduled || route.recurringRuleId != null)
    }
    var frequency by rememberSaveable { mutableStateOf(RecurrenceFrequency.MONTHLY) }
    var intervalCount by rememberSaveable { mutableStateOf("1") }
    var ruleActive by rememberSaveable { mutableStateOf(true) }
    var loaded by rememberSaveable(route.id, route.recurringRuleId) {
        mutableStateOf(route.id == null && route.recurringRuleId == null)
    }
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
    var showFrequencyMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.activity_saved)
    val deletedMessage = stringResource(R.string.activity_deleted)
    val pausedMessage = stringResource(R.string.schedule_paused)
    val resumedMessage = stringResource(R.string.schedule_resumed)

    LaunchedEffect(createdCategoryId, loaded) {
        if (loaded) createdCategoryId?.let {
            categoryId = it
            onCreatedCategoryConsumed()
        }
    }

    LaunchedEffect(accounts, route.id, kind) {
        if (
            route.id == null &&
            route.recurringRuleId == null &&
            kind != ActivityKind.TRANSFER &&
            accountId.isEmpty() &&
            accounts.isNotEmpty()
        ) {
            accountId = accounts.first().id
        }
    }
    LaunchedEffect(accounts, route.id) {
        if (route.id == null && route.recurringRuleId == null && accounts.isNotEmpty()) {
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
            route.recurringRuleId == null &&
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
                    categoryId = it.categoryId
                }
            }
            loaded = true
        }
    }
    LaunchedEffect(route.recurringRuleId) {
        route.recurringRuleId?.let { id ->
            repository.getRecurringRule(id)?.let {
                kind = if (it.transactionType == TransactionType.INCOME) {
                    ActivityKind.INCOME
                } else {
                    ActivityKind.EXPENSE
                }
                accountId = it.accountId
                amount = it.amountMinor.toInputAmount()
                description = it.description.orEmpty()
                occurredAt = it.nextOccurrenceAt
                frequency = it.frequency
                intervalCount = it.intervalCount.toString()
                ruleActive = it.isActive
                isScheduled = true
                categoryId = it.categoryId
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
                                route.recurringRuleId != null ->
                                    R.string.edit_scheduled_transaction

                                isScheduled -> R.string.add_scheduled_transaction
                                route.id == null -> R.string.add_transaction
                                route.kind == ActivityKind.TRANSFER -> R.string.edit_transfer
                                route.kind == ActivityKind.DEBT -> R.string.edit_debt
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
                                    if (isScheduled) {
                                        val input = RecurringRuleInput(
                                            accountId = accountId,
                                            amountMinor = amountMinor,
                                            transactionType = if (kind == ActivityKind.INCOME) {
                                                TransactionType.INCOME
                                            } else {
                                                TransactionType.EXPENSE
                                            },
                                            description = description,
                                            frequency = frequency,
                                            intervalCount = intervalCount.toIntOrNull()
                                                ?: throw IllegalArgumentException(
                                                    "Interval must be greater than zero",
                                                ),
                                            nextOccurrenceAt = occurredAt,
                                            categoryId = categoryId,
                                        )
                                        if (route.recurringRuleId == null) {
                                            repository.createRecurringRule(input)
                                        } else {
                                            repository.updateRecurringRule(
                                                route.recurringRuleId,
                                                input,
                                            )
                                        }
                                    } else if (kind == ActivityKind.TRANSFER) {
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
                                            categoryId = categoryId,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    ActivityKind.INCOME,
                    ActivityKind.EXPENSE,
                    ActivityKind.TRANSFER
                ).forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = kind == option,
                        enabled = route.id == null && route.recurringRuleId == null,
                        onClick = {
                            if (option == ActivityKind.TRANSFER && kind != option) {
                                transferDefaultsApplied = false
                                transferSourceCustomized = false
                                transferDestinationCustomized = false
                            }
                            kind = option
                            if (option == ActivityKind.TRANSFER ||
                                categories.find { it.id == categoryId }?.type?.name != option.name
                            ) {
                                categoryId = null
                            }
                            destinationId = ""
                            if (option == ActivityKind.TRANSFER) {
                                isScheduled = false
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            3,
                        ),
                    ) {
                        Text(
                            stringResource(
                                when (option) {
                                    ActivityKind.INCOME -> R.string.income
                                    ActivityKind.EXPENSE -> R.string.expense
                                    ActivityKind.TRANSFER -> R.string.transfer
                                    ActivityKind.DEBT -> R.string.debt
                                },
                            ),
                        )
                    }
                }
            }
            if (kind != ActivityKind.TRANSFER && route.id == null) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(false, true).forEachIndexed { index, scheduled ->
                        SegmentedButton(
                            selected = isScheduled == scheduled,
                            enabled = route.recurringRuleId == null,
                            onClick = { isScheduled = scheduled },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) {
                            Text(
                                stringResource(
                                    if (scheduled) {
                                        R.string.scheduled
                                    } else {
                                        R.string.one_time
                                    },
                                ),
                            )
                        }
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
            } else {
                CategorySelector(
                    categories = categories.filter { it.type.name == kind.name },
                    selectedId = categoryId,
                    onSelected = { categoryId = it },
                    onAddCategory = { onAddCategory(TransactionType.valueOf(kind.name)) },
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
            if (isScheduled) {
                OutlinedTextField(
                    value = intervalCount,
                    onValueChange = { intervalCount = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.repeat_every)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = showFrequencyMenu,
                    onExpandedChange = { showFrequencyMenu = !showFrequencyMenu },
                ) {
                    OutlinedTextField(
                        value = stringResource(frequency.labelResource()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.frequency)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(showFrequencyMenu)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true,
                            ),
                    )
                    ExposedDropdownMenu(
                        expanded = showFrequencyMenu,
                        onDismissRequest = { showFrequencyMenu = false },
                    ) {
                        RecurrenceFrequency.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelResource())) },
                                onClick = {
                                    frequency = option
                                    showFrequencyMenu = false
                                },
                            )
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "${
                        stringResource(
                            when {
                                route.recurringRuleId != null -> R.string.next_occurrence_label
                                isScheduled -> R.string.first_occurrence
                                else -> R.string.date
                            },
                        )
                    }: ${occurredAt.formattedDate()}",
                )
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
                TextButton(onClick = { onEditSchedule(it) }) {
                    Text(stringResource(R.string.edit_schedule))
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (route.recurringRuleId != null) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                if (ruleActive) {
                                    repository.pauseRecurringRule(route.recurringRuleId)
                                } else {
                                    repository.resumeRecurringRule(route.recurringRuleId)
                                }
                            }.onSuccess {
                                ruleActive = !ruleActive
                                onMessage(if (ruleActive) resumedMessage else pausedMessage)
                            }.onFailure { error = it.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (ruleActive) R.string.pause else R.string.resume,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.delete))
                }
            } else if (route.id != null) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = { confirmDelete = true },
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

    if (confirmDelete && (route.id != null || route.recurringRuleId != null)) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    stringResource(
                        if (route.recurringRuleId != null) {
                            R.string.delete_schedule
                        } else {
                            R.string.delete_activity
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (route.recurringRuleId != null) {
                            R.string.delete_schedule_message
                        } else {
                            R.string.delete_activity_message
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            runCatching {
                                if (route.recurringRuleId != null) {
                                    repository.deleteRecurringRule(route.recurringRuleId)
                                } else if (route.kind == ActivityKind.TRANSFER) {
                                    repository.deleteTransfer(requireNotNull(route.id))
                                } else {
                                    repository.deleteTransaction(requireNotNull(route.id))
                                }
                            }.onSuccess {
                                onMessage(deletedMessage)
                                onFinished()
                            }.onFailure { error = it.message }
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
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun RecurrenceFrequency.labelResource(): Int = when (this) {
    RecurrenceFrequency.DAILY -> R.string.daily
    RecurrenceFrequency.WEEKLY -> R.string.weekly
    RecurrenceFrequency.MONTHLY -> R.string.monthly
    RecurrenceFrequency.YEARLY -> R.string.yearly
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
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
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

@Composable
fun CategorySelector(
    categories: List<CategorySummary>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    onAddCategory: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.find { it.id == selectedId }
    val categoriesById = categories.associateBy(CategorySummary::id)
    val activeCategories = categories.filter { it.archivedAt == null }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.let { category ->
                val label = category.parentId?.let { parentId ->
                    "${categoriesById[parentId]?.name.orEmpty()} · ${category.name}"
                } ?: category.name
                if (category.archivedAt == null) label
                else "$label (${stringResource(R.string.archived)})"
            }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.category)) },
            placeholder = { Text(stringResource(R.string.no_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.no_category)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            activeCategories.filter { it.parentId == null }.forEach { parent ->
                DropdownMenuItem(
                    leadingIcon = { CategoryIcon(parent.iconName, parent.colorIndex) },
                    text = { Text(parent.name) },
                    onClick = {
                        onSelected(parent.id)
                        expanded = false
                    },
                )
                activeCategories.filter { it.parentId == parent.id }.forEach { child ->
                    DropdownMenuItem(
                        leadingIcon = { CategoryIcon(child.iconName, child.colorIndex) },
                        text = { Text("  ${child.name}") },
                        onClick = {
                            onSelected(child.id)
                            expanded = false
                        },
                    )
                }
            }
            if (onAddCategory != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painterResource(LucideR.drawable.lucide_ic_plus),
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.add_category)) },
                    onClick = {
                        expanded = false
                        onAddCategory()
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
