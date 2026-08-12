@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.CategoryInput
import it.rfmariano.denaro.data.finance.CategorySummary
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.finance.StarterCategoryLanguage
import it.rfmariano.denaro.data.local.TransactionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.foundation.lazy.grid.items as gridItems
import com.composables.icons.lucide.R as LucideR

@Composable
fun CategoryManagementScreen(
    repository: FinanceRepository,
    onBack: () -> Unit,
    onEdit: (String?, TransactionType, String?) -> Unit,
) {
    val categories by repository.observeCategories(includeArchived = true)
        .collectAsStateWithLifecycle(emptyList())
    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var starterDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val visible = categories.filter { it.type == type && (showArchived || it.archivedAt == null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null, type, null) }) {
                Icon(
                    painterResource(LucideR.drawable.lucide_ic_plus),
                    contentDescription = stringResource(R.string.add_category),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TransactionType.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = type == option,
                        onClick = { type = option },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) {
                        Text(stringResource(if (option == TransactionType.INCOME) R.string.income else R.string.expense))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.show_archived), modifier = Modifier.weight(1f))
                Switch(checked = showArchived, onCheckedChange = { showArchived = it })
            }
            if (categories.isEmpty()) {
                EmptyState(
                    icon = LucideR.drawable.lucide_ic_shapes,
                    title = stringResource(R.string.no_categories),
                    description = stringResource(R.string.no_categories_description),
                    action = {
                        Button(onClick = { starterDialog = true }) {
                            Text(stringResource(R.string.add_starter_categories))
                        }
                    },
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    visible.filter { it.parentId == null }.forEach { parent ->
                        item(key = parent.id) {
                            CategoryRow(
                                category = parent,
                                prefix = "",
                                onEdit = { onEdit(parent.id, parent.type, null) },
                                onArchive = {
                                    scope.launch {
                                        if (parent.archivedAt == null) repository.archiveCategory(
                                            parent.id
                                        )
                                        else repository.restoreCategory(parent.id)
                                    }
                                },
                            )
                        }
                        items(
                            visible.filter { it.parentId == parent.id },
                            key = CategorySummary::id
                        ) { child ->
                            CategoryRow(
                                category = child,
                                prefix = "    ",
                                onEdit = { onEdit(child.id, child.type, parent.id) },
                                onArchive = {
                                    scope.launch {
                                        if (child.archivedAt == null) repository.archiveCategory(
                                            child.id
                                        )
                                        else repository.restoreCategory(child.id)
                                    }
                                },
                            )
                        }
                        if (parent.archivedAt == null) {
                            item(key = "add-${parent.id}") {
                                TextButton(
                                    onClick = { onEdit(null, type, parent.id) },
                                    modifier = Modifier.padding(start = 56.dp),
                                ) {
                                    Text(stringResource(R.string.add_subcategory))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (starterDialog) {
        AlertDialog(
            onDismissRequest = { starterDialog = false },
            title = { Text(stringResource(R.string.starter_categories_language)) },
            text = { Text(stringResource(R.string.starter_categories_description)) },
            confirmButton = {
                TextButton(onClick = {
                    starterDialog = false
                    scope.launch { repository.applyStarterCategories(StarterCategoryLanguage.ITALIAN) }
                }) { Text("Italiano") }
            },
            dismissButton = {
                TextButton(onClick = {
                    starterDialog = false
                    scope.launch { repository.applyStarterCategories(StarterCategoryLanguage.ENGLISH) }
                }) { Text("English") }
            },
        )
    }
}

@Composable
private fun CategoryRow(
    category: CategorySummary,
    prefix: String,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(prefix)
        CategoryIcon(category.iconName, category.colorIndex)
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.titleMedium)
            if (category.archivedAt != null) Text(
                stringResource(R.string.archived),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onArchive) {
            Icon(
                painterResource(if (category.archivedAt == null) LucideR.drawable.lucide_ic_archive else LucideR.drawable.lucide_ic_archive_restore),
                contentDescription = stringResource(if (category.archivedAt == null) R.string.archive else R.string.restore),
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (categoryId == null) R.string.add_category else R.string.edit_category)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(
                        enabled = loaded && !isSaving,
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
            ParentCategorySelector(
                categories = categories.filter { it.type == type && it.parentId == null && it.id != categoryId },
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
                } else null,
            )
            OutlinedButton(onClick = { iconPicker = true }, modifier = Modifier.fillMaxWidth()) {
                CategoryIcon(iconName, displayedColorIndex)
                Text(
                    categoryIconOption(iconName).name.replace('_', ' '),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(stringResource(R.string.color))
            if (parentId == null && !hasDraftParent) {
                CategoryColorPicker(colorIndex, onSelected = { colorIndex = it })
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            CategoryPalette[displayedColorIndex.mod(CategoryPalette.size)],
                            CircleShape,
                        ),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
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
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.find { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = newParentName ?: selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.parent_category)) },
            placeholder = { Text(stringResource(R.string.none)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.none)) },
                onClick = { onSelected(null); expanded = false })
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = { onSelected(category.id); expanded = false })
            }
            if (onCreateOrEditParent != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painterResource(LucideR.drawable.lucide_ic_plus),
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(
                            stringResource(
                                if (newParentName == null) R.string.create_parent_category
                                else R.string.edit_new_parent,
                            ),
                        )
                    },
                    onClick = {
                        expanded = false
                        onCreateOrEditParent()
                    },
                )
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryPalette.forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .size(if (selectedColorIndex == index) 30.dp else 24.dp)
                    .background(color, CircleShape)
                    .clickable { onSelected(index) },
            )
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
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 76.dp),
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    gridItems(options, key = CategoryIconOption::name) { option ->
                        Column(
                            modifier = Modifier
                                .clickable { onSelected(option.name) }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
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
