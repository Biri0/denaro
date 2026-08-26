package it.rfmariano.denaro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.CurrencyCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyComboBox(
    code: String,
    enabled: Boolean,
    includeAll: Boolean,
    preferredCodes: List<String>,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.currency),
    onCodeChange: (String) -> Unit,
    onValidChange: (Boolean) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val allEntries = remember(locale, includeAll, preferredCodes) {
        CurrencyCatalog.entries(
            locale = locale,
            includeAll = includeAll,
            preferredCodes = preferredCodes,
        )
    }
    var text by rememberSaveable { mutableStateOf(code) }
    LaunchedEffect(code) {
        text = code
    }
    var expanded by remember { mutableStateOf(false) }
    val textIsValid = text.isNotBlank() &&
            allEntries.any { it.code.equals(text.trim(), ignoreCase = true) }
    LaunchedEffect(textIsValid, enabled) {
        if (enabled) onValidChange(textIsValid)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                text = value
                if (!enabled) return@OutlinedTextField
                expanded = true
                allEntries
                    .firstOrNull { it.code.equals(value.trim(), ignoreCase = true) }
                    ?.code
                    ?.let(onCodeChange)
            },
            readOnly = !enabled,
            enabled = enabled,
            label = { Text(label) },
            isError = enabled && text.isNotBlank() && !textIsValid,
            supportingText = {
                if (enabled && text.isNotBlank() && !textIsValid) {
                    Text(stringResource(R.string.unsupported_currency))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            trailingIcon = {
                Box(
                    modifier = Modifier.menuAnchor(
                        ExposedDropdownMenuAnchorType.SecondaryEditable,
                        enabled
                    ),
                ) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
        )

        val query = text.trim()
        val defaultView = allEntries
            .filter { it.code in preferredCodes.toSet() }
        val suggestions = if (query.isBlank()) {
            defaultView
        } else {
            val matched = allEntries
                .filter { CurrencyCatalog.matches(it, query) }
                .filterNot { it.code.equals(code, ignoreCase = true) }
            if (matched.isEmpty()) defaultView else matched
        }
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            suggestions.take(MAX_SUGGESTIONS).forEach { entry ->
                DropdownMenuItem(
                    text = { Text("${entry.code} · ${entry.displayName}") },
                    onClick = {
                        text = entry.code
                        onCodeChange(entry.code)
                        expanded = false
                    },
                )
            }
            when {
                suggestions.size > MAX_SUGGESTIONS -> DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.more_currencies_available,
                                suggestions.size - MAX_SUGGESTIONS,
                            ),
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}

private const val MAX_SUGGESTIONS = 20
