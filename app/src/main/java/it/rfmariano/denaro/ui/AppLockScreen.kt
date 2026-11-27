@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package it.rfmariano.denaro.ui

import androidx.activity.compose.ReportDrawn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.security.DeviceAuthenticationResult
import it.rfmariano.denaro.data.security.DeviceAuthenticator
import it.rfmariano.denaro.data.security.ProcessUnlockSession
import kotlinx.coroutines.launch

@Composable
fun AppLockGate(
    appLockEnabled: Boolean,
    processUnlocked: Boolean,
    authenticator: DeviceAuthenticator,
    processUnlockSession: ProcessUnlockSession,
    content: @Composable () -> Unit,
) {
    var authenticatedLocally by remember(appLockEnabled, processUnlocked) {
        mutableStateOf(false)
    }
    if (shouldRevealApp(appLockEnabled, processUnlocked, authenticatedLocally)) {
        content()
        return
    }

    val scope = rememberCoroutineScope()
    var authenticating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var needsSecuritySetup by remember { mutableStateOf(false) }
    val promptTitle = stringResource(R.string.unlock_denaro)
    val promptSubtitle = stringResource(R.string.unlock_prompt_description)

    fun authenticate() {
        if (authenticating) return
        scope.launch {
            authenticating = true
            message = null
            needsSecuritySetup = false
            when (authenticator.authenticate(promptTitle, promptSubtitle)) {
                DeviceAuthenticationResult.Success -> {
                    authenticatedLocally = true
                    processUnlockSession.unlock()
                }

                DeviceAuthenticationResult.Canceled -> message = R.string.authentication_canceled
                DeviceAuthenticationResult.NotConfigured -> {
                    message = R.string.device_security_not_configured
                    needsSecuritySetup = true
                }

                DeviceAuthenticationResult.Error -> message = R.string.authentication_unavailable
            }
            authenticating = false
        }
    }

    LaunchedEffect(Unit) {
        authenticate()
    }

    ReportDrawn()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DenaroIconBadge(
            icon = R.drawable.ms_shield,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 84.dp,
            iconSize = 40.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.denaro_locked),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.denaro_locked_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = ::authenticate,
            enabled = !authenticating,
        ) {
            if (authenticating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.unlock))
            }
        }
        if (needsSecuritySetup) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = authenticator::openSecuritySettings) {
                Text(stringResource(R.string.set_up_device_security))
            }
        }
    }
}

internal fun shouldRevealApp(
    appLockEnabled: Boolean,
    processUnlocked: Boolean,
    authenticatedLocally: Boolean,
): Boolean = !appLockEnabled || processUnlocked || authenticatedLocally

@Composable
fun StartupLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DenaroLoadingIndicator(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
