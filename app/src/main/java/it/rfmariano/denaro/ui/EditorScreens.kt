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
import androidx.compose.ui.platform.LocalConfiguration
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
import it.rfmariano.denaro.data.finance.CurrencyCatalog
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.finance.RecurringRuleInput
import it.rfmariano.denaro.data.finance.TransactionInput
import it.rfmariano.denaro.data.finance.TransferAccountSuggestions
import it.rfmariano.denaro.data.finance.TransferInput
import it.rfmariano.denaro.data.local.RecurrenceFrequency
import it.rfmariano.denaro.data.local.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import com.composables.icons.lucide.R as LucideR

@Composable
fun AccountEditorScreen(
    repository: FinanceRepository,
    defaultCurrency: String = CurrencyCatalog.localeCurrencyCode(
        LocalConfiguration.current.locales[0],
    ) ?: "EUR",
    accountId: String?,
    usedCurrencies: List<String> = emptyList(),
    includeAll: Boolean = false,
    fractionDigitsForNewAccount: suspend (String) -> Int = repository::getFractionDigitsForNewAccount,
    onFinished: (String) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var openingBalance by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable(accountId) { mutableStateOf(defaultCurrency) }
    var userPickedCurrency by rememberSaveable(accountId) { mutableStateOf(false) }
    var currencyValid by rememberSaveable(accountId) { mutableStateOf(true) }
    var loaded by rememberSaveable(accountId) { mutableStateOf(accountId == null) }
    var openingBalanceScale by rememberSaveable(accountId) { mutableStateOf(2) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appLocale = LocalConfiguration.current.locales[0]
    val savedMessage = stringResource(R.string.account_saved)
    val unsupportedCurrencyMessage = stringResource(R.string.unsupported_currency)

    LaunchedEffect(accountId) {
        if (accountId != null) {
            repository.observeAccount(accountId).first { it != null }?.let { account ->
                name = account.name
                description = account.description.orEmpty()
                openingBalanceScale = account.fractionDigits
                openingBalance = Money.toInputAmount(
                    account.openingBalanceMinor,
                    account.fractionDigits,
                )
                currency = account.currency
                loaded = true
            }
        }
    }

    LaunchedEffect(defaultCurrency) {
        if (accountId == null && !userPickedCurrency) {
            currency = defaultCurrency
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
                        enabled = loaded && !isSaving && (accountId != null || currencyValid),
                        onClick = {
                            if (isSaving) return@TextButton
                            isSaving = true
                            error = null
                            scope.launch {
                                if (accountId == null && !CurrencyCatalog.isValid(currency)) {
                                    error = unsupportedCurrencyMessage
                                    isSaving = false
                                    return@launch
                                }
                                runCatching {
                                    val fractionDigits = if (accountId == null) {
                                        fractionDigitsForNewAccount(currency)
                                    } else {
                                        openingBalanceScale
                                    }
                                    val input = AccountInput(
                                        name = name,
                                        description = description,
                                        openingBalanceMinor = Money.parseMinorUnits(
                                            openingBalance.ifBlank { "0.00" },
                                            fractionDigits,
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.DecimalSigned),
                singleLine = true,
                suffix = { Text(currency) },
                supportingText = {
                    Text(stringResource(R.string.opening_balance_warning))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            CurrencyComboBox(
                code = currency,
                enabled = accountId == null,
                includeAll = includeAll,
                preferredCodes = remember(usedCurrencies, appLocale) {
                    CurrencyCatalog.preferredCodes(usedCurrencies, appLocale)
                },
                onCodeChange = {
                    currency = it
                    userPickedCurrency = true
                },
                onValidChange = { currencyValid = it },
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
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
    val allAccounts by repository.observeAllAccounts().collectAsStateWithLifecycle(emptyList())
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
    var initializedTransactionKind by rememberSaveable(route.id, route.recurringRuleId) {
        mutableStateOf<ActivityKind?>(null)
    }
    var transactionAccountCustomized by rememberSaveable(route.id, route.recurringRuleId) {
        mutableStateOf(false)
    }
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
    val selectedAccount = accounts.firstOrNull { it.id == accountId }

    LaunchedEffect(createdCategoryId, loaded) {
        if (loaded) createdCategoryId?.let {
            categoryId = it
            onCreatedCategoryConsumed()
        }
    }

    LaunchedEffect(accounts, route.id, route.recurringRuleId, kind) {
        if (
            route.id == null &&
            route.recurringRuleId == null &&
            kind != ActivityKind.TRANSFER &&
            initializedTransactionKind != kind &&
            accounts.isNotEmpty()
        ) {
            val routeAccountId = route.accountId?.takeIf { requestedId ->
                accounts.any { it.id == requestedId }
            }
            if (routeAccountId != null) {
                if (!transactionAccountCustomized) accountId = routeAccountId
            } else {
                val preferredAccountId = repository.getPreferredTransactionAccountId(
                    TransactionType.valueOf(kind.name),
                )?.takeIf { preferredId -> accounts.any { it.id == preferredId } }
                if (!transactionAccountCustomized) {
                    accountId = preferredAccountId ?: accounts.first().id
                }
            }
            initializedTransactionKind = kind
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
    LaunchedEffect(route.id, accounts, allAccounts) {
        val id = route.id ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        if (route.kind == ActivityKind.TRANSFER) {
            val transfer = repository.getTransfer(id) ?: return@LaunchedEffect
            val account = allAccounts.firstOrNull { it.id == transfer.fromAccountId }
                ?: return@LaunchedEffect
            accountId = transfer.fromAccountId
            destinationId = transfer.toAccountId
            amount = Money.toInputAmount(transfer.amountMinor, account.fractionDigits)
            description = transfer.description.orEmpty()
            occurredAt = transfer.occurredAt
        } else {
            val transaction = repository.getTransaction(id) ?: return@LaunchedEffect
            val account = allAccounts.firstOrNull { it.id == transaction.accountId }
                ?: return@LaunchedEffect
            kind = if (transaction.type == TransactionType.INCOME) {
                ActivityKind.INCOME
            } else {
                ActivityKind.EXPENSE
            }
            accountId = transaction.accountId
            amount = Money.toInputAmount(transaction.amountMinor, account.fractionDigits)
            description = transaction.description.orEmpty()
            occurredAt = transaction.occurredAt
            recurringRuleId = transaction.recurringRuleId
            categoryId = transaction.categoryId
        }
        loaded = true
    }
    LaunchedEffect(route.recurringRuleId, accounts, allAccounts) {
        val id = route.recurringRuleId ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        val rule = repository.getRecurringRule(id) ?: return@LaunchedEffect
        val account = allAccounts.firstOrNull { it.id == rule.accountId } ?: return@LaunchedEffect
        kind = if (rule.transactionType == TransactionType.INCOME) {
            ActivityKind.INCOME
        } else {
            ActivityKind.EXPENSE
        }
        accountId = rule.accountId
        amount = Money.toInputAmount(rule.amountMinor, account.fractionDigits)
        description = rule.description.orEmpty()
        occurredAt = rule.nextOccurrenceAt
        frequency = rule.frequency
        intervalCount = rule.intervalCount.toString()
        ruleActive = rule.isActive
        isScheduled = true
        categoryId = rule.categoryId
        loaded = true
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
                        enabled = loaded && selectedAccount != null && !isSaving,
                        onClick = {
                            if (isSaving) return@TextButton
                            val account = selectedAccount ?: return@TextButton
                            isSaving = true
                            error = null
                            scope.launch {
                                runCatching {
                                    val amountMinor = Money.parseMinorUnits(
                                        amount,
                                        account.fractionDigits,
                                    )
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
                            showFrequencyMenu = false
                            if (option == ActivityKind.TRANSFER && kind != option) {
                                transferDefaultsApplied = false
                                transferSourceCustomized = false
                                transferDestinationCustomized = false
                            }
                            kind = option
                            initializedTransactionKind = null
                            transactionAccountCustomized = false
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
                                    ActivityKind.ADJUSTMENT -> R.string.balance_adjustment
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
                enabled = loaded,
                onSelected = {
                    amount = Money.rescaleInput(
                        amount,
                        accounts.firstOrNull { account -> account.id == accountId }?.fractionDigits,
                        accounts.firstOrNull { account -> account.id == it }?.fractionDigits,
                    )
                    accountId = it
                    if (kind == ActivityKind.TRANSFER) {
                        transferSourceCustomized = true
                        transferDestinationCustomized = false
                        destinationId = suggestedDestinationId(
                            sourceId = it,
                            accounts = accounts,
                            suggestions = transferSuggestions,
                        )
                    } else {
                        transactionAccountCustomized = true
                        if (destinationId == it) destinationId = ""
                    }
                },
            )
            if (kind == ActivityKind.TRANSFER) {
                val sourceCurrency = selectedAccount?.currency
                val sourceFractionDigits = selectedAccount?.fractionDigits
                AccountSelector(
                    label = stringResource(R.string.to_account),
                    accounts = accounts.filter {
                        it.id != accountId &&
                                it.currency == sourceCurrency &&
                                it.fractionDigits == sourceFractionDigits
                    },
                    selectedId = destinationId,
                    enabled = loaded,
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
                enabled = loaded,
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
    val source = accounts.find { it.id == sourceId }
    val sourceCurrency = source?.currency
    val sourceFractionDigits = source?.fractionDigits
    return accounts.singleOrNull {
        it.id != sourceId &&
                it.currency == sourceCurrency &&
                it.fractionDigits == sourceFractionDigits
    }?.id.orEmpty()
}

@Composable
fun AccountSelector(
    label: String,
    accounts: List<AccountSummary>,
    selectedId: String,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    val selected = accounts.find { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.select_account)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled,
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

private fun Long.toUtcDateMillis(): Long {
    val date = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun Long.withTimeFrom(existing: Long): Long {
    val date = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val time = Instant.ofEpochMilli(existing).atZone(ZoneId.systemDefault()).toLocalTime()
    return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
