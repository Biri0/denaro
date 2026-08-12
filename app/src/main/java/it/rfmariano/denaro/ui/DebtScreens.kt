@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import it.rfmariano.denaro.data.finance.CounterpartyInput
import it.rfmariano.denaro.data.finance.CounterpartySummary
import it.rfmariano.denaro.data.finance.DebtInput
import it.rfmariano.denaro.data.finance.DebtRepaymentInput
import it.rfmariano.denaro.data.finance.DebtSummary
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.local.DebtDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import com.composables.icons.lucide.R as LucideR

private enum class DebtStatusFilter { OPEN, SETTLED }

@Composable
fun DebtsPanel(
    debts: List<DebtSummary>,
    amountsVisible: Boolean,
    onAddDebt: () -> Unit,
    onDebtClick: (String) -> Unit,
) {
    var direction by rememberSaveable { mutableStateOf<DebtDirection?>(null) }
    var status by rememberSaveable { mutableStateOf(DebtStatusFilter.OPEN) }
    val today = LocalDate.now()
    val visible = debts.filter { debt ->
        (direction == null || debt.direction == direction) &&
                (if (status == DebtStatusFilter.OPEN) !debt.isSettled else debt.isSettled)
    }.sortedWith(
        compareBy<DebtSummary> {
            when {
                it.isSettled -> 2
                it.dueDate != null && LocalDate.parse(it.dueDate) < today -> 0
                else -> 1
            }
        }.thenBy { it.dueDate ?: "9999-12-31" }.thenByDescending { it.openedAt },
    )
    Column(Modifier.fillMaxSize()) {
        val openDebts = debts.filterNot(DebtSummary::isSettled)
        openDebts.groupBy { it.currency }.toSortedMap().forEach { (currency, group) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DebtTotal(
                    R.string.you_owe,
                    group.filter { it.direction == DebtDirection.BORROWED }
                        .sumOf { it.outstandingMinor },
                    currency,
                    amountsVisible,
                    Modifier.weight(1f),
                )
                DebtTotal(
                    R.string.owed_to_you,
                    group.filter { it.direction == DebtDirection.LENT }
                        .sumOf { it.outstandingMinor },
                    currency,
                    amountsVisible,
                    Modifier.weight(1f),
                )
            }
        }
        Row(
            Modifier
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
                                }
                            )
                        )
                    },
                )
            }
        }
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            DebtStatusFilter.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = status == option,
                    onClick = { status = option },
                    shape = SegmentedButtonDefaults.itemShape(index, DebtStatusFilter.entries.size),
                ) { Text(stringResource(if (option == DebtStatusFilter.OPEN) R.string.open else R.string.settled)) }
            }
        }
        if (visible.isEmpty()) {
            EmptyState(
                icon = LucideR.drawable.lucide_ic_hand_coins,
                title = stringResource(R.string.no_debts),
                description = stringResource(R.string.no_debts_description),
                modifier = Modifier.fillMaxSize(),
                action = { Button(onClick = onAddDebt) { Text(stringResource(R.string.add_debt)) } },
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(visible, key = { it.id }) { debt ->
                    DebtRow(debt, amountsVisible) { onDebtClick(debt.id) }
                    HorizontalDivider(Modifier.padding(start = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun DebtTotal(
    label: Int,
    amount: Long,
    currency: String,
    visible: Boolean,
    modifier: Modifier
) {
    Column(modifier.padding(12.dp)) {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (visible) Money.format(amount, currency) else stringResource(R.string.amount_hidden),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun DebtRow(debt: DebtSummary, amountsVisible: Boolean, onClick: () -> Unit) {
    val today = LocalDate.now()
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(painterResource(LucideR.drawable.lucide_ic_hand_coins), contentDescription = null)
        },
        headlineContent = { Text(debt.counterpartyName) },
        supportingContent = {
            Column {
                Text(stringResource(if (debt.direction == DebtDirection.BORROWED) R.string.borrowed else R.string.lent))
                debt.dueDate?.let { due ->
                    val date = LocalDate.parse(due)
                    Text(
                        when {
                            !debt.isSettled && date < today -> stringResource(R.string.overdue)
                            !debt.isSettled && date == today -> stringResource(R.string.due_today)
                            else -> stringResource(R.string.due_on, date.formattedDate())
                        },
                        color = if (!debt.isSettled && date < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            Column {
                Text(
                    if (amountsVisible) Money.format(
                        debt.outstandingMinor,
                        debt.currency
                    ) else stringResource(R.string.amount_hidden)
                )
                Text(
                    stringResource(if (debt.isSettled) R.string.settled else R.string.outstanding),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
    )
}

@Composable
fun DebtDetailScreen(
    state: DebtDetailUiState,
    accounts: List<it.rfmariano.denaro.data.finance.AccountSummary>,
    amountsVisible: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddRepayment: (String) -> Unit,
    onEditRepayment: (String, String) -> Unit,
) {
    val debt = state.debt
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(debt?.counterpartyName.orEmpty()) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    debt?.let {
                        IconButton(onClick = { onEdit(it.id) }) {
                            Icon(
                                painterResource(LucideR.drawable.lucide_ic_pencil),
                                contentDescription = stringResource(R.string.edit_debt)
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (debt != null) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(if (debt.direction == DebtDirection.BORROWED) R.string.you_owe else R.string.owed_to_you))
                        Text(
                            if (amountsVisible) Money.format(
                                debt.outstandingMinor,
                                debt.currency
                            ) else stringResource(R.string.amount_hidden),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "${stringResource(R.string.principal)}: ${
                                if (amountsVisible) Money.format(
                                    debt.principalMinor,
                                    debt.currency
                                ) else stringResource(R.string.amount_hidden)
                            }"
                        )
                        Text(
                            "${stringResource(R.string.repaid)}: ${
                                if (amountsVisible) Money.format(
                                    debt.repaidMinor,
                                    debt.currency
                                ) else stringResource(R.string.amount_hidden)
                            }"
                        )
                        Text(debt.accountName)
                        debt.dueDate?.let {
                            Text(
                                stringResource(
                                    R.string.due_on,
                                    LocalDate.parse(it).formattedDate()
                                )
                            )
                        }
                        debt.note?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!debt.isSettled) {
                            Button(
                                onClick = { onAddRepayment(debt.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.add_repayment))
                            }
                        }
                    }
                }
                if (state.repayments.isNotEmpty()) item { SectionHeader(stringResource(R.string.repayments)) }
                items(state.repayments, key = { it.id }) { payment ->
                    val account = accounts.find { it.id == payment.accountId }?.name.orEmpty()
                    ListItem(
                        modifier = Modifier.clickable { onEditRepayment(debt.id, payment.id) },
                        headlineContent = {
                            Text(
                                if (amountsVisible) Money.format(
                                    payment.amountMinor,
                                    debt.currency
                                ) else stringResource(R.string.amount_hidden)
                            )
                        },
                        supportingContent = {
                            Text(
                                listOf(
                                    account,
                                    payment.occurredAt.formattedDate()
                                ).filter(String::isNotBlank).joinToString(" · ")
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DebtEditorScreen(
    repository: FinanceRepository,
    debtId: String?,
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    onDeleted: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val accounts by remember(repository) { repository.observeAllAccounts() }.collectAsStateWithLifecycle(
        emptyList()
    )
    val counterparties by remember(repository) { repository.observeCounterparties(includeArchived = true) }.collectAsStateWithLifecycle(
        emptyList()
    )
    var direction by rememberSaveable { mutableStateOf(DebtDirection.BORROWED) }
    var counterpartyId by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var openedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var dueDate by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var hasRepayments by rememberSaveable { mutableStateOf(false) }
    var loaded by rememberSaveable(debtId) { mutableStateOf(debtId == null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var counterpartyMenu by remember { mutableStateOf(false) }
    var showNewCounterparty by remember { mutableStateOf(false) }
    var showOpenedPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.debt_saved)
    val deleted = stringResource(R.string.debt_deleted)

    LaunchedEffect(accounts, counterparties) {
        if (debtId == null) {
            if (accountId.isBlank()) accountId =
                accounts.firstOrNull { it.archivedAt == null }?.id.orEmpty()
            if (counterpartyId.isBlank()) counterpartyId =
                counterparties.firstOrNull { it.archivedAt == null }?.id.orEmpty()
        }
    }
    LaunchedEffect(debtId) {
        debtId?.let { id ->
            repository.observeDebt(id).first { it != null }?.let { debt ->
                direction = debt.direction
                counterpartyId = debt.counterpartyId
                accountId = debt.accountId
                amount = debt.principalMinor.inputAmount()
                openedAt = debt.openedAt
                dueDate = debt.dueDate
                note = debt.note.orEmpty()
                hasRepayments = debt.repaidMinor > 0
                loaded = true
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (debtId == null) R.string.add_debt else R.string.edit_debt)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(enabled = loaded && !saving, onClick = {
                        saving = true
                        scope.launch {
                            runCatching {
                                val input = DebtInput(
                                    counterpartyId,
                                    accountId,
                                    direction,
                                    Money.parseMinorUnits(amount),
                                    openedAt,
                                    dueDate,
                                    note
                                )
                                if (debtId == null) repository.createDebt(input) else {
                                    repository.updateDebt(debtId, input); debtId
                                }
                            }.onSuccess { onMessage(saved); onFinished(it) }
                                .onFailure { error = it.message; saving = false }
                        }
                    }) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DebtDirection.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = direction == option,
                        enabled = !hasRepayments,
                        onClick = { direction = option },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(stringResource(if (option == DebtDirection.BORROWED) R.string.borrowed else R.string.lent)) }
                }
            }
            ExposedDropdownMenuBox(
                expanded = counterpartyMenu,
                onExpandedChange = { counterpartyMenu = !counterpartyMenu }) {
                OutlinedTextField(
                    value = counterparties.find { it.id == counterpartyId }?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.counterparty)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(counterpartyMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                )
                ExposedDropdownMenu(
                    expanded = counterpartyMenu,
                    onDismissRequest = { counterpartyMenu = false }) {
                    counterparties.filter { it.archivedAt == null || it.id == counterpartyId }
                        .forEach { party ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        listOfNotNull(
                                            party.name,
                                            party.note
                                        ).joinToString(" · ")
                                    )
                                },
                                onClick = { counterpartyId = party.id; counterpartyMenu = false },
                            )
                        }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_counterparty)) },
                        onClick = { counterpartyMenu = false; showNewCounterparty = true })
                }
            }
            AccountSelector(
                label = stringResource(if (direction == DebtDirection.BORROWED) R.string.received_into else R.string.paid_from),
                accounts = accounts.filter { it.archivedAt == null || it.id == accountId },
                selectedId = accountId,
                onSelected = { accountId = it },
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
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
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.counterparty_note)) },
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (debtId != null) OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.delete)) }
        }
    }
    if (showNewCounterparty) CounterpartyDialog(
        null,
        onDismiss = { showNewCounterparty = false }) { input ->
        scope.launch {
            runCatching { repository.createCounterparty(input) }.onSuccess {
                counterpartyId = it; showNewCounterparty = false
            }.onFailure { error = it.message }
        }
    }
    if (showOpenedPicker) LocalDatePicker(openedAt, { showOpenedPicker = false }) {
        openedAt = it; showOpenedPicker = false
    }
    if (showDuePicker) LocalDatePicker(dueDate?.let {
        LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } ?: openedAt, { showDuePicker = false }) {
        dueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            .toString(); showDuePicker = false
    }
    if (confirmDelete && debtId != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_debt)) },
        text = { Text(stringResource(R.string.delete_debt_message)) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    repository.deleteDebt(debtId); onMessage(
                    deleted
                ); onDeleted()
                }
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = {
                confirmDelete = false
            }) { Text(stringResource(R.string.cancel)) }
        },
    )
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
    val debt by remember(
        repository,
        debtId
    ) { repository.observeDebt(debtId) }.collectAsStateWithLifecycle(null)
    val accounts by remember(repository) { repository.observeActiveAccounts() }.collectAsStateWithLifecycle(
        emptyList()
    )
    var accountId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var occurredAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var note by rememberSaveable { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.repayment_saved)
    LaunchedEffect(debt, accounts) {
        if (accountId.isBlank()) accountId =
            accounts.firstOrNull { it.currency == debt?.currency }?.id.orEmpty()
    }
    LaunchedEffect(repaymentId) {
        repaymentId?.let { repository.getDebtRepayment(it) }?.let {
            accountId = it.accountId; amount = it.amountMinor.inputAmount(); occurredAt =
            it.occurredAt; note = it.note.orEmpty()
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(if (repaymentId == null) R.string.add_repayment else R.string.edit_repayment)) },
            navigationIcon = { BackButton(onBack) },
            actions = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        runCatching {
                            val input = DebtRepaymentInput(
                                debtId,
                                accountId,
                                Money.parseMinorUnits(amount),
                                occurredAt,
                                note
                            )
                            if (repaymentId == null) repository.createDebtRepayment(input) else repository.updateDebtRepayment(
                                repaymentId,
                                input
                            )
                        }.onSuccess { onMessage(saved); onFinished() }
                            .onFailure { error = it.message; saving = false }
                    }
                }) { Text(stringResource(R.string.save)) }
            })
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccountSelector(
                stringResource(R.string.account),
                accounts.filter { it.currency == debt?.currency },
                accountId,
                { accountId = it })
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            DateField(
                label = R.string.date,
                value = occurredAt.formattedDate(),
                onClick = { showDate = true },
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.counterparty_note)) },
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (repaymentId != null) OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.delete)) }
        }
    }
    if (showDate) LocalDatePicker(occurredAt, { showDate = false }) {
        occurredAt = it; showDate = false
    }
    if (confirmDelete && repaymentId != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_repayment)) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    repository.deleteDebtRepayment(
                        repaymentId
                    ); onFinished()
                }
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = {
                confirmDelete = false
            }) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun CounterpartiesScreen(repository: FinanceRepository, onBack: () -> Unit) {
    val parties by remember(repository) { repository.observeCounterparties(includeArchived = true) }.collectAsStateWithLifecycle(
        emptyList()
    )
    var editing by remember { mutableStateOf<CounterpartySummary?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.counterparties)) },
            navigationIcon = { BackButton(onBack) },
            actions = {
                IconButton(onClick = {
                    adding = true
                }) {
                    Icon(
                        painterResource(LucideR.drawable.lucide_ic_plus),
                        contentDescription = stringResource(R.string.add_counterparty)
                    )
                }
            })
    }) { padding ->
        if (parties.isEmpty()) EmptyState(
            icon = LucideR.drawable.lucide_ic_users,
            title = stringResource(R.string.no_counterparties),
            description = stringResource(R.string.manage_counterparties),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            action = {
                Button(onClick = {
                    adding = true
                }) { Text(stringResource(R.string.add_counterparty)) }
            },
        )
        else LazyColumn(Modifier
            .fillMaxSize()
            .padding(padding)) {
            items(parties, key = { it.id }) { party ->
                ListItem(
                    modifier = Modifier.clickable { editing = party },
                    headlineContent = { Text(party.name) },
                    supportingContent = { Text(party.note.orEmpty()) },
                    trailingContent = {
                        TextButton(onClick = {
                            scope.launch {
                                repository.setCounterpartyArchived(
                                    party.id,
                                    party.archivedAt == null
                                )
                            }
                        }) { Text(stringResource(if (party.archivedAt == null) R.string.archive else R.string.restore)) }
                    },
                )
            }
        }
    }
    if (adding) CounterpartyDialog(
        null,
        { adding = false }) { scope.launch { repository.createCounterparty(it); adding = false } }
    editing?.let { party ->
        CounterpartyDialog(
            party,
            { editing = null }) {
            scope.launch {
                repository.updateCounterparty(
                    party.id,
                    it
                ); editing = null
            }
        }
    }
}

@Composable
private fun CounterpartyDialog(
    existing: CounterpartySummary?,
    onDismiss: () -> Unit,
    onSave: (CounterpartyInput) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_counterparty else R.string.edit_counterparty)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(R.string.name)) })
                OutlinedTextField(
                    note,
                    { note = it },
                    label = { Text(stringResource(R.string.counterparty_note)) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        CounterpartyInput(
                            name,
                            note
                        )
                    )
                }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
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
        contentPadding = PaddingValues(start = 24.dp, end = 8.dp),
    ) {
        Text(
            text = "${stringResource(label)}: ${value.ifBlank { stringResource(R.string.none) }}",
            modifier = Modifier.weight(1f),
        )
        onClear?.let { clear ->
            IconButton(onClick = clear) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_x),
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) {
        DatePicker(
            state
        )
    }
}

private fun Long.inputAmount(): String =
    BigDecimal.valueOf(this, 2).stripTrailingZeros().toPlainString()

private fun Long.utcDateMillis(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.localNoonMillis(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun LocalDate.formattedDate(): String =
    atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().formattedDate()
