@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.SupportedCurrencies
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository
import it.rfmariano.denaro.data.preferences.LanguageOption
import it.rfmariano.denaro.data.preferences.ThemeMode
import com.composables.icons.lucide.R as LucideR

private enum class SettingsDialog {
    THEME,
    LANGUAGE,
    CURRENCY,
}

@Composable
fun SettingsScreen(
    preferencesRepository: AppPreferencesRepository,
    onBack: () -> Unit,
    onCategories: () -> Unit,
) {
    val preferences by preferencesRepository.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Icon(
                            painterResource(LucideR.drawable.lucide_ic_arrow_left),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(R.string.appearance)
            SettingsItem(
                title = stringResource(R.string.theme),
                value = themeLabel(preferences.themeMode),
                onClick = { dialog = SettingsDialog.THEME },
            )
            SettingsItem(
                title = stringResource(R.string.language),
                value = languageLabel(preferences.language),
                onClick = { dialog = SettingsDialog.LANGUAGE },
            )

            SectionLabel(R.string.general)
            SettingsItem(
                title = stringResource(R.string.default_currency),
                value = preferences.defaultCurrency,
                onClick = { dialog = SettingsDialog.CURRENCY },
            )
            SettingsItem(
                title = stringResource(R.string.categories),
                value = stringResource(R.string.manage_categories),
                onClick = onCategories,
            )
        }
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.theme),
            choices = ThemeMode.entries,
            selected = preferences.themeMode,
            label = { themeLabel(it) },
            onSelect = {
                preferencesRepository.setThemeMode(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.language),
            choices = LanguageOption.entries,
            selected = preferences.language,
            label = { languageLabel(it) },
            onSelect = {
                preferencesRepository.setLanguage(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.CURRENCY -> ChoiceDialog(
            title = stringResource(R.string.default_currency),
            choices = SupportedCurrencies,
            selected = preferences.defaultCurrency,
            label = { it },
            onSelect = {
                preferencesRepository.setDefaultCurrency(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

@Composable
private fun SectionLabel(label: Int) {
    Text(
        text = stringResource(label),
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp),
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsItem(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = choice == selected,
                            onClick = { onSelect(choice) },
                        )
                        Text(
                            text = label(choice),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun themeLabel(value: ThemeMode): String = stringResource(
    when (value) {
        ThemeMode.SYSTEM -> R.string.system_default
        ThemeMode.LIGHT -> R.string.light
        ThemeMode.DARK -> R.string.dark
    },
)

@Composable
private fun languageLabel(value: LanguageOption): String = stringResource(
    when (value) {
        LanguageOption.SYSTEM -> R.string.system_default
        LanguageOption.ENGLISH -> R.string.english
        LanguageOption.ITALIAN -> R.string.italian
    },
)
