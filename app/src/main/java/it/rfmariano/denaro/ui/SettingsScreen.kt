@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package it.rfmariano.denaro.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.FinanceSessionProvider
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository
import it.rfmariano.denaro.data.preferences.DefaultScreen
import it.rfmariano.denaro.data.preferences.LanguageOption
import it.rfmariano.denaro.data.preferences.ThemeMode
import it.rfmariano.denaro.data.security.DeviceAuthenticationResult
import it.rfmariano.denaro.data.security.DeviceAuthenticator
import it.rfmariano.denaro.data.security.ProcessUnlockSession
import it.rfmariano.denaro.data.security.SecurityPreferencesRepository
import it.rfmariano.denaro.ui.theme.supportsDynamicColor
import kotlinx.coroutines.launch

private enum class SettingsDialog {
    THEME,
    LANGUAGE,
    DEFAULT_SCREEN,
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
    onExportStatement: () -> Unit,
) {
    val preferences by preferencesRepository.state.collectAsStateWithLifecycle()
    val securityPreferences by securityPreferencesRepository.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var authenticationInProgress by remember { mutableStateOf(false) }
    var showSecuritySetupDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val authenticationTitle = stringResource(R.string.confirm_security_change)
    val authenticationSubtitle = stringResource(R.string.confirm_security_change_description)
    val authenticationCanceled = stringResource(R.string.authentication_canceled)
    val authenticationUnavailable = stringResource(R.string.authentication_unavailable)
    val dynamicColorSupported = supportsDynamicColor()

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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { BackButton(onBack) },
                colors = denaroTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionLabel(R.string.appearance)
            val appearanceCount = if (dynamicColorSupported) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SettingsItem(
                    title = stringResource(R.string.theme),
                    value = themeLabel(preferences.themeMode),
                    shapes = ListItemDefaults.segmentedShapes(0, appearanceCount),
                    onClick = { dialog = SettingsDialog.THEME },
                )
                if (dynamicColorSupported) {
                    SwitchSettingsItem(
                        title = stringResource(R.string.dynamic_color),
                        value = stringResource(R.string.dynamic_color_description),
                        checked = preferences.dynamicColor,
                        shapes = ListItemDefaults.segmentedShapes(1, appearanceCount),
                        onCheckedChange = preferencesRepository::setDynamicColor,
                    )
                }
                SettingsItem(
                    title = stringResource(R.string.language),
                    value = languageLabel(preferences.language),
                    shapes = ListItemDefaults.segmentedShapes(appearanceCount - 1, appearanceCount),
                    onClick = { dialog = SettingsDialog.LANGUAGE },
                )
            }

            SectionLabel(R.string.security)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SwitchSettingsItem(
                    title = stringResource(R.string.app_lock),
                    value = stringResource(R.string.app_lock_description),
                    checked = securityPreferences.appLockEnabled,
                    enabled = !authenticationInProgress,
                    shapes = ListItemDefaults.segmentedShapes(0, 2),
                    onCheckedChange = ::setAppLockEnabled,
                )
                SwitchSettingsItem(
                    title = stringResource(R.string.screen_security),
                    value = stringResource(R.string.screen_security_description),
                    checked = securityPreferences.screenSecurityEnabled,
                    shapes = ListItemDefaults.segmentedShapes(1, 2),
                    onCheckedChange = securityPreferencesRepository::setScreenSecurityEnabled,
                )
            }

            SectionLabel(R.string.general)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SettingsItem(
                    title = stringResource(R.string.default_screen),
                    value = defaultScreenLabel(preferences.defaultScreen),
                    shapes = ListItemDefaults.segmentedShapes(0, 4),
                    onClick = { dialog = SettingsDialog.DEFAULT_SCREEN },
                )
                SwitchSettingsItem(
                    title = stringResource(R.string.show_all_currencies),
                    value = stringResource(R.string.show_all_currencies_description),
                    checked = preferences.showAllCurrencies,
                    shapes = ListItemDefaults.segmentedShapes(1, 4),
                    onCheckedChange = preferencesRepository::setShowAllCurrencies,
                )
                SettingsItem(
                    title = stringResource(R.string.categories),
                    value = stringResource(R.string.manage_categories),
                    shapes = ListItemDefaults.segmentedShapes(2, 4),
                    onClick = onCategories,
                )
                SettingsItem(
                    title = stringResource(R.string.counterparties),
                    value = stringResource(R.string.manage_counterparties),
                    shapes = ListItemDefaults.segmentedShapes(3, 4),
                    onClick = onCounterparties,
                )
            }

            SectionLabel(R.string.data)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SettingsItem(
                    title = stringResource(R.string.export_statement),
                    value = stringResource(R.string.export_statement_description),
                    shapes = ListItemDefaults.segmentedShapes(0, 4),
                    onClick = onExportStatement,
                )
                BackupSettings(
                    financeSessionProvider = financeSessionProvider,
                    snackbarHostState = snackbarHostState,
                )
            }

            DeveloperSettingsSection(financeSessionProvider)
            Spacer(Modifier.height(24.dp))
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

        SettingsDialog.DEFAULT_SCREEN -> ChoiceDialog(
            title = stringResource(R.string.default_screen),
            choices = DefaultScreen.entries,
            selected = preferences.defaultScreen,
            label = { defaultScreenLabel(it) },
            onSelect = {
                preferencesRepository.setDefaultScreen(it)
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
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, end = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsItem(
    title: String,
    value: String,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = denaroSegmentedItemColors(),
        supportingContent = { Text(value) },
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@Composable
private fun SwitchSettingsItem(
    title: String,
    value: String,
    checked: Boolean,
    shapes: ListItemShapes,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shapes = shapes,
        colors = denaroSegmentedItemColors(),
        supportingContent = { Text(value) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMediumEmphasized)
    }
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
private fun languageLabel(value: LanguageOption): String = when (value) {
    LanguageOption.SYSTEM -> stringResource(R.string.system_default)
    LanguageOption.ENGLISH -> stringResource(R.string.english)
    LanguageOption.ITALIAN -> stringResource(R.string.italian)
    LanguageOption.SPANISH -> "Español"
}

@Composable
private fun defaultScreenLabel(value: DefaultScreen): String = stringResource(
    when (value) {
        DefaultScreen.HOME -> R.string.home
        DefaultScreen.ACCOUNTS -> R.string.accounts
        DefaultScreen.ACTIVITY -> R.string.activity
        DefaultScreen.DEBTS -> R.string.debts
    },
)
