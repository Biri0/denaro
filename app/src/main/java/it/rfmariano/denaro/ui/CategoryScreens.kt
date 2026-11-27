@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.AccountSummary
import it.rfmariano.denaro.data.finance.BudgetSummary
import it.rfmariano.denaro.data.finance.CategoryInput
import it.rfmariano.denaro.data.finance.CategorySummary
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.Money
import it.rfmariano.denaro.data.finance.starterCategoryLanguage
import it.rfmariano.denaro.data.local.TransactionType
import it.rfmariano.denaro.ui.theme.tabularNumbers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun CategoryManagementScreen(
    repository: FinanceRepository,
    onBack: () -> Unit,
    onEdit: (String?, TransactionType, String?) -> Unit,
) {
    val categories by repository.observeCategories(includeArchived = true)
        .collectAsStateWithLifecycle(emptyList())
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<CategorySummary?>(null) }
    var deletingInProgress by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleteFailedMessage = stringResource(R.string.category_delete_failed)
    val starterLanguage = starterCategoryLanguage(
        LocalConfiguration.current.locales[0].language,
    )
    val visible = categories.filter { it.type == type && (showArchived || it.archivedAt == null) }
    val showSearch = shouldUseSearchableSelector(visible.size)
    LaunchedEffect(showSearch, type) {
        if (!showSearch) searchQuery = ""
    }
    val filteredVisible = remember(visible, searchQuery, showSearch) {
        if (!showSearch || searchQuery.isBlank()) {
            visible
        } else {
            filterCategoriesForSearch(visible, searchQuery)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.categories)) },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(null, type, null) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painterResource(R.drawable.ms_add),
                    contentDescription = stringResource(R.string.add_category),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DenaroSegmentedChoiceRow(
                options = TransactionType.entries,
                selectedOption = type,
                onSelect = { type = it },
                label = { option ->
                    stringResource(
                        if (option == TransactionType.INCOME) R.string.income else R.string.expense,
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.show_archived),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = showArchived, onCheckedChange = { showArchived = it })
            }
            if (showSearch) {
                InlineSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (categories.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ms_category,
                    title = stringResource(R.string.no_categories),
                    description = stringResource(R.string.no_categories_description),
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.applyStarterCategories(starterLanguage)
                                }
                            },
                        ) {
                            Text(stringResource(R.string.add_starter_categories))
                        }
                    },
                )
            } else if (filteredVisible.isEmpty() && showSearch && searchQuery.isNotBlank()) {
                Text(
                    text = stringResource(R.string.no_matching_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            } else {
                val parents = filteredVisible.filter { it.parentId == null }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 104.dp,
                    ),
                ) {
                    parents.forEach { parent ->
                        val children = filteredVisible.filter { it.parentId == parent.id }
                        val groupCount = 1 + children.size
                        item(key = parent.id) {
                            CategoryRow(
                                category = parent,
                                indented = false,
                                shapes = ListItemDefaults.segmentedShapes(0, groupCount),
                                onEdit = { onEdit(parent.id, parent.type, null) },
                                onArchive = {
                                    scope.launch {
                                        if (parent.archivedAt == null) {
                                            repository.archiveCategory(parent.id)
                                        } else {
                                            repository.restoreCategory(parent.id)
                                        }
                                    }
                                },
                                deletable = parent.usageCount == 0,
                                onDelete = { deleting = parent },
                            )
                            if (children.isNotEmpty()) {
                                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                            }
                        }
                        itemsIndexed(children, key = { _, child -> child.id }) { childIdx, child ->
                            CategoryRow(
                                category = child,
                                indented = true,
                                shapes = ListItemDefaults.segmentedShapes(childIdx + 1, groupCount),
                                onEdit = { onEdit(child.id, child.type, parent.id) },
                                onArchive = {
                                    scope.launch {
                                        if (child.archivedAt == null) {
                                            repository.archiveCategory(child.id)
                                        } else {
                                            repository.restoreCategory(child.id)
                                        }
                                    }
                                },
                                deletable = child.usageCount == 0,
                                onDelete = { deleting = child },
                            )
                            if (childIdx < children.lastIndex) {
                                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                            }
                        }
                        if (parent.archivedAt == null) {
                            item(key = "add-${parent.id}") {
                                TextButton(
                                    onClick = { onEdit(null, type, parent.id) },
                                    modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ms_add),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.add_subcategory),
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        } else {
                            item(key = "spacer-${parent.id}") {
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { category ->
        AlertDialog(
            onDismissRequest = { if (!deletingInProgress) deleting = null },
            title = { Text(stringResource(R.string.delete_category)) },
            text = {
                Text(stringResource(R.string.delete_category_message, category.name))
            },
            confirmButton = {
                TextButton(
                    enabled = !deletingInProgress,
                    onClick = {
                        deletingInProgress = true
                        scope.launch {
                            runCatching { repository.deleteCategory(category.id) }
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
private fun CategoryRow(
    category: CategorySummary,
    indented: Boolean,
    shapes: ListItemShapes,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    deletable: Boolean,
    onDelete: () -> Unit,
) {
    SegmentedListItem(
        onClick = onEdit,
        shapes = shapes,
        colors = denaroSegmentedItemColors(
            containerColor = if (indented) {
                MaterialTheme.colorScheme.surfaceContainerLowest
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        leadingContent = {
            Box(modifier = Modifier.padding(start = if (indented) 20.dp else 0.dp)) {
                CategoryIcon(category.iconName, category.colorIndex)
            }
        },
        supportingContent = if (category.archivedAt != null) {
            { Text(stringResource(R.string.archived)) }
        } else {
            null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (deletable) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.ms_delete),
                            contentDescription = stringResource(R.string.delete_category_action),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = onArchive) {
                    Icon(
                        painterResource(
                            if (category.archivedAt == null) {
                                R.drawable.ms_archive
                            } else {
                                R.drawable.ms_unarchive
                            },
                        ),
                        contentDescription = stringResource(
                            if (category.archivedAt == null) R.string.archive else R.string.restore,
                        ),
                    )
                }
            }
        },
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CategoryEditorScreen(
    repository: FinanceRepository,
    categoryId: String?,
    type: TransactionType,
    initialParentId: String?,
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
) {
    val categories by repository.observeCategories().collectAsStateWithLifecycle(emptyList())
    var name by rememberSaveable { mutableStateOf("") }
    var parentId by rememberSaveable { mutableStateOf(initialParentId) }
    var iconName by rememberSaveable { mutableStateOf("shapes") }
    var iconManuallySelected by rememberSaveable { mutableStateOf(false) }
    var colorIndex by rememberSaveable { mutableStateOf(Random.nextInt(CategoryPalette.size)) }
    var iconPicker by remember { mutableStateOf(false) }
    var hasDraftParent by rememberSaveable { mutableStateOf(false) }
    var draftParentName by rememberSaveable { mutableStateOf("") }
    var draftParentIconName by rememberSaveable { mutableStateOf("shapes") }
    var draftParentColorIndex by rememberSaveable { mutableStateOf(0) }
    var parentDialog by rememberSaveable { mutableStateOf(false) }
    var parentDialogName by rememberSaveable { mutableStateOf("") }
    var parentDialogIconName by rememberSaveable { mutableStateOf("shapes") }
    var parentDialogIconManuallySelected by rememberSaveable { mutableStateOf(false) }
    var parentDialogColorIndex by rememberSaveable { mutableStateOf(0) }
    var parentIconPicker by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loaded by rememberSaveable(categoryId) { mutableStateOf(categoryId == null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val budgets by repository.observeBudgets().collectAsStateWithLifecycle(emptyList())
    val accounts by repository.observeActiveAccounts().collectAsStateWithLifecycle(emptyList())
    var budgetEdits by rememberSaveable(categoryId) { mutableStateOf(emptyMap<String, String>()) }
    var budgetDialogOpen by rememberSaveable(categoryId) { mutableStateOf(false) }
    var budgetDialogCurrency by rememberSaveable(categoryId) { mutableStateOf("") }
    var budgetDialogAmount by rememberSaveable(categoryId) { mutableStateOf("") }
    var budgetDialogError by rememberSaveable(categoryId) { mutableStateOf<String?>(null) }
    var budgetDialogCurrencyValid by remember { mutableStateOf(false) }
    val displayedColorIndex = when {
        hasDraftParent -> draftParentColorIndex
        parentId != null -> categories.find { it.id == parentId }?.colorIndex ?: colorIndex
        else -> colorIndex
    }

    LaunchedEffect(categoryId) {
        categoryId?.let { id ->
            repository.getCategory(id)?.let {
                name = it.name
                parentId = it.parentId
                iconName = it.iconName
                colorIndex = it.colorIndex
                iconManuallySelected = true
            }
            loaded = true
        }
    }

    LaunchedEffect(name, iconManuallySelected, loaded) {
        if (loaded && !iconManuallySelected) {
            delay(CATEGORY_ICON_SUGGESTION_DEBOUNCE_MS)
            iconName = suggestCategoryIcon(name)
        }
    }

    LaunchedEffect(parentDialogName, parentDialogIconManuallySelected, parentDialog) {
        if (parentDialog && !parentDialogIconManuallySelected) {
            delay(CATEGORY_ICON_SUGGESTION_DEBOUNCE_MS)
            parentDialogIconName = suggestCategoryIcon(parentDialogName)
        }
    }

    val budgetVisible =
        type == TransactionType.EXPENSE && parentId == null && !hasDraftParent
    val categoryBudgets = budgets.filter { it.categoryId == categoryId }
    val budgetPreferredCodes =
        (accounts.map { it.currency } + categoryBudgets.map { it.currency })
            .distinct()
            .sorted()
    val budgetRows = resolveBudgetRows(
        existing = categoryBudgets,
        edits = budgetEdits,
        digitsFor = { fractionDigitsFor(accounts, it) },
    )
    val usedBudgetCurrencies = budgetRows.map { it.first }
    val canAddBudget = budgetPreferredCodes.any { it !in usedBudgetCurrencies }
    val currencyError = stringResource(R.string.unsupported_currency)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (categoryId == null) R.string.add_category else R.string.edit_category,
                        ),
                    )
                },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
                actions = {
                    FilledTonalButton(
                        enabled = loaded && !isSaving,
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = {
                            isSaving = true
                            scope.launch {
                                runCatching {
                                    val resolvedIcon = if (iconManuallySelected) {
                                        iconName
                                    } else {
                                        suggestCategoryIcon(name)
                                    }
                                    val input = CategoryInput(
                                        type,
                                        parentId,
                                        name,
                                        resolvedIcon,
                                        displayedColorIndex,
                                    )
                                    val budgetValid =
                                        type == TransactionType.EXPENSE &&
                                                parentId == null &&
                                                !hasDraftParent
                                    val budgetPlan = if (budgetValid) {
                                        parseBudgetPlan(budgetEdits) {
                                            fractionDigitsFor(accounts, it)
                                        }
                                    } else {
                                        emptyMap()
                                    }
                                    val resolvedCategoryId =
                                        if (categoryId == null && hasDraftParent) {
                                            repository.createCategoryWithNewParent(
                                                parentInput = CategoryInput(
                                                    type = type,
                                                    parentId = null,
                                                    name = draftParentName,
                                                    iconName = draftParentIconName,
                                                    colorIndex = draftParentColorIndex,
                                                ),
                                                childInput = input.copy(parentId = null),
                                            )
                                        } else if (categoryId == null) {
                                            repository.createCategory(input)
                                        } else {
                                            repository.updateCategory(categoryId, input)
                                            categoryId
                                        }
                                    if (budgetValid) {
                                        repository.applyBudgetPlan(resolvedCategoryId, budgetPlan)
                                    } else {
                                        repository.clearBudgets(resolvedCategoryId)
                                    }
                                    resolvedCategoryId
                                }.onSuccess(onFinished)
                                    .onFailure {
                                        error = it.message
                                        isSaving = false
                                    }
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
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.large,
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ParentCategorySelector(
                        categories = categories.filter {
                            it.type == type && it.parentId == null && it.id != categoryId
                        },
                        selectedId = parentId,
                        newParentName = draftParentName.takeIf { hasDraftParent },
                        onSelected = {
                            parentId = it
                            hasDraftParent = false
                        },
                        onCreateOrEditParent = if (categoryId == null) {
                            {
                                if (hasDraftParent) {
                                    parentDialogName = draftParentName
                                    parentDialogIconName = draftParentIconName
                                    parentDialogIconManuallySelected = true
                                    parentDialogColorIndex = draftParentColorIndex
                                } else {
                                    parentDialogName = ""
                                    parentDialogIconName = "shapes"
                                    parentDialogIconManuallySelected = false
                                    parentDialogColorIndex = Random.nextInt(CategoryPalette.size)
                                }
                                parentDialog = true
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            DenaroSectionCard(contentPadding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedButton(
                        onClick = { iconPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CategoryIcon(iconName, displayedColorIndex)
                        Text(
                            text = categoryIconOption(iconName).name.replace('_', ' '),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.color),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (parentId == null && !hasDraftParent) {
                        CategoryColorPicker(colorIndex, onSelected = { colorIndex = it })
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    CategoryPalette[displayedColorIndex.mod(CategoryPalette.size)],
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
            if (loaded && budgetVisible && budgetPreferredCodes.isNotEmpty()) {
                DenaroSectionCard(contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.budgets),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        if (budgetRows.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_budgets_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        budgetRows.forEach { (currency, amount) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        budgetDialogCurrency = currency
                                        budgetDialogAmount = amount
                                        budgetDialogError = null
                                        budgetDialogOpen = true
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = currency,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = amount,
                                    style = MaterialTheme.typography.titleMediumEmphasized.tabularNumbers(),
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                budgetDialogCurrency = budgetPreferredCodes
                                    .firstOrNull { it !in usedBudgetCurrencies }
                                    ?: ""
                                budgetDialogAmount = ""
                                budgetDialogError = null
                                budgetDialogOpen = true
                            },
                            enabled = canAddBudget,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ms_add),
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.add_budget),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        if (budgetRows.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.manage_budgets),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (loaded && !budgetVisible && categoryBudgets.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.budget_removed_on_subcategory),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (budgetDialogOpen) {
        val existingCurrencies = budgetRows.map { it.first }
        val isEditing = budgetDialogCurrency in existingCurrencies
        AlertDialog(
            onDismissRequest = { budgetDialogOpen = false },
            title = {
                Text(
                    stringResource(if (isEditing) R.string.edit_budget else R.string.add_budget),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CurrencyComboBox(
                        code = budgetDialogCurrency,
                        enabled = !isEditing,
                        includeAll = true,
                        preferredCodes = budgetPreferredCodes.filterNot {
                            it in existingCurrencies
                        },
                        allowedCodes = budgetPreferredCodes.toSet(),
                        onCodeChange = { budgetDialogCurrency = it },
                        onValidChange = { budgetDialogCurrencyValid = it },
                    )
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.large,
                        value = budgetDialogAmount,
                        onValueChange = { budgetDialogAmount = it },
                        label = { Text(stringResource(R.string.monthly_limit)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    budgetDialogError?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            require(isEditing || budgetDialogCurrencyValid) { currencyError }
                            Money.parseMinorUnits(
                                budgetDialogAmount.trim(),
                                fractionDigitsFor(accounts, budgetDialogCurrency),
                            )
                        }.fold(
                            onSuccess = {
                                budgetEdits =
                                    budgetEdits + (budgetDialogCurrency to budgetDialogAmount.trim())
                                budgetDialogError = null
                                budgetDialogOpen = false
                            },
                            onFailure = { budgetDialogError = it.message },
                        )
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEditing) {
                        TextButton(
                            onClick = {
                                budgetEdits = budgetEdits + (budgetDialogCurrency to "")
                                budgetDialogError = null
                                budgetDialogOpen = false
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    TextButton(onClick = { budgetDialogOpen = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }

    if (iconPicker) {
        CategoryIconPickerDialog(
            colorIndex = displayedColorIndex,
            onSelected = {
                iconName = it
                iconManuallySelected = true
                iconPicker = false
            },
            onDismiss = { iconPicker = false },
        )
    }

    if (parentDialog) {
        AlertDialog(
            onDismissRequest = { parentDialog = false },
            title = {
                Text(
                    stringResource(
                        if (hasDraftParent) R.string.edit_new_parent
                        else R.string.create_parent_category,
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.large,
                        value = parentDialogName,
                        onValueChange = { parentDialogName = it },
                        label = { Text(stringResource(R.string.parent_category_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { parentIconPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CategoryIcon(parentDialogIconName, parentDialogColorIndex)
                        Text(
                            categoryIconOption(parentDialogIconName).name.replace('_', ' '),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Text(stringResource(R.string.color))
                    CategoryColorPicker(
                        selectedColorIndex = parentDialogColorIndex,
                        onSelected = { parentDialogColorIndex = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = parentDialogName.isNotBlank(),
                    onClick = {
                        draftParentName = parentDialogName.trim()
                        draftParentIconName = if (parentDialogIconManuallySelected) {
                            parentDialogIconName
                        } else {
                            suggestCategoryIcon(parentDialogName)
                        }
                        draftParentColorIndex = parentDialogColorIndex
                        hasDraftParent = true
                        parentId = null
                        parentDialog = false
                    },
                ) { Text(stringResource(R.string.use_parent)) }
            },
            dismissButton = {
                TextButton(onClick = { parentDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (parentIconPicker) {
        CategoryIconPickerDialog(
            colorIndex = parentDialogColorIndex,
            onSelected = {
                parentDialogIconName = it
                parentDialogIconManuallySelected = true
                parentIconPicker = false
            },
            onDismiss = { parentIconPicker = false },
        )
    }
}

@Composable
private fun ParentCategorySelector(
    categories: List<CategorySummary>,
    selectedId: String?,
    newParentName: String?,
    onSelected: (String?) -> Unit,
    onCreateOrEditParent: (() -> Unit)?,
) {
    val selected = categories.find { it.id == selectedId }
    AdaptiveSelector(
        label = stringResource(R.string.parent_category),
        placeholder = stringResource(R.string.none),
        selectedText = newParentName ?: selected?.name.orEmpty(),
        options = categories.map { category ->
            SelectorOption<String?>(
                id = category.id,
                value = category.id,
                label = category.name,
                selected = newParentName == null && category.id == selectedId,
                leadingContent = { CategoryIcon(category.iconName, category.colorIndex) },
            )
        },
        onSelected = onSelected,
        clearOption = SelectorOption(
            id = "__none__",
            value = null,
            label = stringResource(R.string.none),
            selected = newParentName == null && selectedId == null,
        ),
        footerAction = onCreateOrEditParent?.let { createOrEdit ->
            SelectorAction(
                label = stringResource(
                    if (newParentName == null) R.string.create_parent_category
                    else R.string.edit_new_parent,
                ),
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ms_add),
                        contentDescription = null,
                    )
                },
                onClick = { createOrEdit() },
            )
        },
    )
}

internal fun filterCategoriesForSearch(
    categories: List<CategorySummary>,
    query: String,
): List<CategorySummary> {
    if (query.isBlank()) return categories
    val parents = categories.filter { it.parentId == null }
    val childrenByParent = categories.filter { it.parentId != null }.groupBy { it.parentId }
    return buildList {
        parents.forEach { parent ->
            val children = childrenByParent[parent.id].orEmpty()
            val parentMatches = matchesSelectorSearch(query, listOf(parent.name))
            val matchingChildren = if (parentMatches) {
                children
            } else {
                children.filter { child ->
                    matchesSelectorSearch(query, listOf(child.name, parent.name))
                }
            }
            if (parentMatches || matchingChildren.isNotEmpty()) {
                add(parent)
                addAll(matchingChildren)
            }
        }
    }
}

@Composable
private fun CategoryColorPicker(
    selectedColorIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryPalette.forEachIndexed { index, color ->
            val selected = selectedColorIndex == index
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color, CircleShape)
                    .then(
                        if (selected) {
                            Modifier.border(
                                2.5.dp,
                                MaterialTheme.colorScheme.onSurface,
                                CircleShape
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        painter = painterResource(R.drawable.ms_check),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryIconPickerDialog(
    colorIndex: Int,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(query) { searchCategoryIcons(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_icon)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InlineSearchField(
                    query = query,
                    onQueryChange = { query = it },
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 76.dp),
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    gridItems(options, key = CategoryIconOption::name) { option ->
                        Column(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .clickable { onSelected(option.name) }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            CategoryIcon(option.name, colorIndex)
                            Text(
                                text = option.name.replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun fractionDigitsFor(accounts: List<AccountSummary>, currency: String): Int =
    accounts.firstOrNull { it.currency == currency }?.fractionDigits
        ?: Money.fractionDigitsForCurrency(currency)

internal fun parseBudgetPlan(
    edits: Map<String, String>,
    digitsFor: (String) -> Int,
): Map<String, Long?> = edits.mapValues { (currency, text) ->
    require(currency.isNotBlank()) { "Unsupported currency" }
    if (text.isBlank()) null else Money.parseMinorUnits(text.trim(), digitsFor(currency))
}

internal fun resolveBudgetRows(
    existing: List<BudgetSummary>,
    edits: Map<String, String>,
    digitsFor: (String) -> Int,
): List<Pair<String, String>> {
    val stored = existing.associateBy(BudgetSummary::currency)
    return (stored.keys + edits.keys)
        .sorted()
        .mapNotNull { currency ->
            val amount = edits[currency]
                ?: stored[currency]?.let {
                    Money.toInputAmount(it.amountMinor, digitsFor(currency))
                }
                ?: return@mapNotNull null
            amount.takeIf(String::isNotBlank)?.let { currency to it }
        }
}
