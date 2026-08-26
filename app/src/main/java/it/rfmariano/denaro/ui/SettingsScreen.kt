@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.FinanceSessionProvider
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository
import it.rfmariano.denaro.data.preferences.LanguageOption
import it.rfmariano.denaro.data.preferences.ThemeMode
import it.rfmariano.denaro.data.security.DeviceAuthenticationResult
import it.rfmariano.denaro.data.security.DeviceAuthenticator
import it.rfmariano.denaro.data.security.ProcessUnlockSession
import it.rfmariano.denaro.data.security.SecurityPreferencesRepository
import kotlinx.coroutines.launch
import com.composables.icons.lucide.R as LucideR

private enum class SettingsDialog {
    THEME,
    LANGUAGE,
}

@Composable
fun SettingsScreen(
    financeSessionProvider: FinanceSessionProvider,
    preferencesRepository: AppPreferencesRepository,
    securityPreferencesRepository: SecurityPreferencesRepository,
    processUnlockSession: ProcessUnlockSession,
    authenticator: DeviceAuthenticator,
    scrollState: ScrollState,
    onBack: () -> Unit,
    onCategories: () -> Unit,
    onCounterparties: () -> Unit,
) {
    val preferences by preferencesRepository.state.collectAsStateWithLifecycle()
    val securityPreferences by securityPreferencesRepository.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var authenticationInProgress by remember { mutableStateOf(false) }
    var showSecuritySetupDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val authenticationTitle = stringResource(R.string.confirm_security_change)
    val authenticationSubtitle = stringResource(R.string.confirm_security_change_description)
    val authenticationCanceled = stringResource(R.string.authentication_canceled)
    val authenticationUnavailable = stringResource(R.string.authentication_unavailable)

    fun setAppLockEnabled(enabled: Boolean) {
        if (authenticationInProgress) return
        scope.launch {
            authenticationInProgress = true
            when (authenticator.authenticate(authenticationTitle, authenticationSubtitle)) {
                DeviceAuthenticationResult.Success -> {
                    securityPreferencesRepository.setAppLockEnabled(enabled)
                    processUnlockSession.unlock()
                }

                DeviceAuthenticationResult.Canceled -> {
                    snackbarHostState.showSnackbar(authenticationCanceled)
                }

                DeviceAuthenticationResult.NotConfigured -> {
                    showSecuritySetupDialog = true
                }

                DeviceAuthenticationResult.Error -> {
                    snackbarHostState.showSnackbar(authenticationUnavailable)
                }
            }
            authenticationInProgress = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .verticalScroll(scrollState),
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

            SectionLabel(R.string.security)
            SwitchSettingsItem(
                title = stringResource(R.string.app_lock),
                value = stringResource(R.string.app_lock_description),
                checked = securityPreferences.appLockEnabled,
                enabled = !authenticationInProgress,
                onCheckedChange = ::setAppLockEnabled,
            )
            SwitchSettingsItem(
                title = stringResource(R.string.screen_security),
                value = stringResource(R.string.screen_security_description),
                checked = securityPreferences.screenSecurityEnabled,
                onCheckedChange = securityPreferencesRepository::setScreenSecurityEnabled,
            )

            SectionLabel(R.string.general)
            SwitchSettingsItem(
                title = stringResource(R.string.show_all_currencies),
                value = stringResource(R.string.show_all_currencies_description),
                checked = preferences.showAllCurrencies,
                onCheckedChange = preferencesRepository::setShowAllCurrencies,
            )
            SettingsItem(
                title = stringResource(R.string.categories),
                value = stringResource(R.string.manage_categories),
                onClick = onCategories,
            )
            SettingsItem(
                title = stringResource(R.string.counterparties),
                value = stringResource(R.string.manage_counterparties),
                onClick = onCounterparties,
            )
            SectionLabel(R.string.data)
            BackupSettings(
                financeSessionProvider = financeSessionProvider,
                snackbarHostState = snackbarHostState,
            )
            DeveloperSettingsSection(financeSessionProvider)
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

        null -> Unit
    }

    if (showSecuritySetupDialog) {
        AlertDialog(
            onDismissRequest = { showSecuritySetupDialog = false },
            title = { Text(stringResource(R.string.device_security_required)) },
            text = { Text(stringResource(R.string.device_security_required_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSecuritySetupDialog = false
                        authenticator.openSecuritySettings()
                    },
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecuritySetupDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
private fun SwitchSettingsItem(
    title: String,
    value: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        modifier = Modifier.clickable(enabled = enabled) {
            onCheckedChange(!checked)
        },
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
