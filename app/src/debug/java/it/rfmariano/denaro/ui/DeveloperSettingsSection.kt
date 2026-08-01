package it.rfmariano.denaro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.FinanceSessionProvider
import kotlinx.coroutines.launch

@Composable
internal fun DeveloperSettingsSection(provider: FinanceSessionProvider) {
    val session by provider.session.collectAsStateWithLifecycle()
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val enabled = session?.isDemo == true

    Text(
        text = stringResource(R.string.developer_options),
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.demo_mode)) },
        supportingContent = {
            Text(
                stringResource(
                    if (failed) R.string.demo_mode_failed else R.string.demo_mode_description,
                ),
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                enabled = !busy,
                onCheckedChange = null,
            )
        },
        modifier = Modifier.clickable(enabled = !busy) {
            busy = true
            failed = false
            scope.launch {
                runCatching { provider.setDemoMode(!enabled, localeTag) }
                    .onFailure { failed = true }
                busy = false
            }
        },
    )
}
