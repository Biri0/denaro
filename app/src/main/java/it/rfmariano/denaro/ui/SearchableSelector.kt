@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import it.rfmariano.denaro.R
import java.text.Normalizer
import com.composables.icons.lucide.R as LucideR

internal const val SELECTOR_SEARCH_THRESHOLD = 10

internal fun shouldUseSearchableSelector(
    optionCount: Int,
    threshold: Int = SELECTOR_SEARCH_THRESHOLD,
): Boolean = optionCount > threshold

private val COMBINING_DIACRITICS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")

internal fun String.normalizedSelectorText(): String = Normalizer.normalize(
    lowercase().trim(),
    Normalizer.Form.NFD,
).replace(COMBINING_DIACRITICS, "")

internal fun matchesSelectorSearch(
    query: String,
    candidates: List<String>,
): Boolean {
    val normalizedQuery = query.normalizedSelectorText()
    if (normalizedQuery.isEmpty()) return true
    val normalizedCandidates = candidates
        .map(String::normalizedSelectorText)
        .filter(String::isNotEmpty)
    if (normalizedCandidates.isEmpty()) return false
    val tokens = normalizedQuery.split(WHITESPACE).filter(String::isNotEmpty)
    return tokens.all { token ->
        normalizedCandidates.any { candidate -> candidate.contains(token) }
    }
}

internal data class SelectorOption<T>(
    val id: String,
    val value: T,
    val label: String,
    val supportingText: String? = null,
    val supportingTextWhenFiltered: String? = null,
    val searchTerms: List<String> = listOfNotNull(
        label,
        supportingText,
        supportingTextWhenFiltered
    ),
    val selected: Boolean = false,
    val indented: Boolean = false,
    val leadingContent: (@Composable () -> Unit)? = null,
)

internal data class SelectorAction(
    val label: String,
    val leadingIcon: (@Composable () -> Unit)? = null,
    val onClick: (query: String) -> Unit,
)

internal fun <T> filterSelectorOptions(
    options: List<SelectorOption<T>>,
    query: String,
): List<SelectorOption<T>> = if (query.isBlank()) {
    options
} else {
    options.filter { option -> matchesSelectorSearch(query, option.searchTerms) }
}

@Composable
internal fun InlineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search)) },
        singleLine = true,
        leadingIcon = {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_search),
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_x),
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun <T> AdaptiveSelector(
    label: String,
    selectedText: String,
    options: List<SelectorOption<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    clearOption: SelectorOption<T>? = null,
    footerAction: SelectorAction? = null,
    searchThreshold: Int = SELECTOR_SEARCH_THRESHOLD,
) {
    val useBottomSheet = shouldUseSearchableSelector(options.size, searchThreshold)
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    LaunchedEffect(expanded) {
        if (!expanded) query = ""
    }

    ExposedDropdownMenuBox(
        expanded = expanded && !useBottomSheet,
        onExpandedChange = {
            if (enabled) {
                expanded = if (useBottomSheet) true else it
            }
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled,
                ),
        )
        if (!useBottomSheet) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                clearOption?.let { option ->
                    DropdownMenuItem(
                        leadingIcon = option.leadingContent,
                        text = { Text(option.label) },
                        onClick = {
                            onSelected(option.value)
                            expanded = false
                        },
                    )
                }
                options.forEach { option ->
                    val prefix = if (option.indented) "  " else ""
                    val dropdownText = listOfNotNull(
                        option.label,
                        option.supportingText?.takeIf(String::isNotBlank),
                    ).joinToString(" · ")
                    DropdownMenuItem(
                        leadingIcon = option.leadingContent,
                        text = { Text("$prefix$dropdownText") },
                        onClick = {
                            onSelected(option.value)
                            expanded = false
                        },
                    )
                }
                footerAction?.let { action ->
                    if (clearOption != null || options.isNotEmpty()) {
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        leadingIcon = action.leadingIcon,
                        text = { Text(action.label) },
                        onClick = {
                            expanded = false
                            action.onClick("")
                        },
                    )
                }
            }
        }
    }

    if (useBottomSheet && expanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isFiltering = query.isNotBlank()
        val filteredOptions = remember(options, query) {
            filterSelectorOptions(options, query)
        }
        val showClearOption = clearOption != null &&
                (!isFiltering || matchesSelectorSearch(query, clearOption.searchTerms))

        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                InlineSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = if (footerAction == null) 16.dp else 4.dp),
                ) {
                    if (showClearOption) {
                        item(key = "__clear__") {
                            SelectorSheetRow(
                                option = clearOption,
                                isFiltering = isFiltering,
                                onClick = {
                                    onSelected(clearOption.value)
                                    expanded = false
                                },
                            )
                        }
                    }
                    if (filteredOptions.isEmpty() && !showClearOption) {
                        item(key = "__empty__") {
                            Text(
                                text = stringResource(R.string.no_matching_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                            )
                        }
                    } else {
                        items(filteredOptions, key = SelectorOption<T>::id) { option ->
                            SelectorSheetRow(
                                option = option,
                                isFiltering = isFiltering,
                                onClick = {
                                    onSelected(option.value)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                footerAction?.let { action ->
                    HorizontalDivider()
                    ListItem(
                        headlineContent = {
                            Text(
                                text = action.label,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        leadingContent = action.leadingIcon,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            val trimmedQuery = query.trim()
                            expanded = false
                            action.onClick(trimmedQuery)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SelectorSheetRow(
    option: SelectorOption<T>,
    isFiltering: Boolean,
    onClick: () -> Unit,
) {
    val supporting = if (isFiltering) {
        option.supportingTextWhenFiltered ?: option.supportingText
    } else {
        option.supportingText
    }?.takeIf(String::isNotBlank)
    val startPadding = if (option.indented && !isFiltering) 16.dp else 0.dp
    ListItem(
        headlineContent = {
            Text(
                text = option.label,
                color = if (option.selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Unspecified
                },
            )
        },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = option.leadingContent,
        trailingContent = if (option.selected) {
            {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = startPadding),
    )
}
