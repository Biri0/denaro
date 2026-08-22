package it.rfmariano.denaro

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.ReportDrawn
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.rfmariano.denaro.data.migration.MigrationResult
import it.rfmariano.denaro.data.migration.MigrationViewModel
import it.rfmariano.denaro.data.preferences.ThemeMode
import it.rfmariano.denaro.data.security.AndroidDeviceAuthenticator
import it.rfmariano.denaro.quickentry.QuickEntryIntent
import it.rfmariano.denaro.quickentry.QuickEntryRequest
import it.rfmariano.denaro.ui.AppLockGate
import it.rfmariano.denaro.ui.DenaroApp
import it.rfmariano.denaro.ui.DenaroAppPreloader
import it.rfmariano.denaro.ui.DenaroAppState
import it.rfmariano.denaro.ui.StartupLoading
import it.rfmariano.denaro.ui.rememberDenaroAppState
import it.rfmariano.denaro.ui.theme.DenaroTheme

class MainActivity : AppCompatActivity() {
    private val migrationViewModel by viewModels<MigrationViewModel>()
    private var quickEntryRequest by mutableStateOf<QuickEntryRequest?>(null)
    private var quickEntrySequence = 0L

    override fun onStart() {
        val denaroApplication = application as DenaroApplication
        denaroApplication.processUnlockSession.onAppForegrounded(
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            appLockEnabled = denaroApplication.securityPreferencesRepository
                .state.value.appLockEnabled,
        )
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            (application as DenaroApplication).processUnlockSession.onAppBackgrounded(
                SystemClock.elapsedRealtime(),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        acceptQuickEntryIntent(intent)
        val denaroApplication = application as DenaroApplication
        val financeSessionProvider = denaroApplication.financeSessionProvider
        val securityPreferencesRepository = denaroApplication.securityPreferencesRepository
        val processUnlockSession = denaroApplication.processUnlockSession
        denaroApplication.preferencesRepository.applyStoredLanguage()
        applyScreenSecurity(securityPreferencesRepository.state.value.screenSecurityEnabled)
        splashScreen.setKeepOnScreenCondition {
            shouldKeepSplashVisible(
                migrationResult = migrationViewModel.result.value,
                hasFinanceSession = financeSessionProvider.session.value != null,
                appLocked = securityPreferencesRepository.state.value.appLockEnabled &&
                        !processUnlockSession.unlocked.value,
            )
        }
        enableEdgeToEdge()
        setContent {
            val preferences by denaroApplication.preferencesRepository.state
                .collectAsStateWithLifecycle()
            val migrationResult by migrationViewModel.result.collectAsStateWithLifecycle()
            val financeSession by financeSessionProvider.session.collectAsStateWithLifecycle()
            val securityPreferences by securityPreferencesRepository.state
                .collectAsStateWithLifecycle()
            val processUnlocked by processUnlockSession.unlocked.collectAsStateWithLifecycle()
            val authenticator = remember { AndroidDeviceAuthenticator(this) }
            LaunchedEffect(securityPreferences.screenSecurityEnabled) {
                applyScreenSecurity(securityPreferences.screenSecurityEnabled)
            }
            LaunchedEffect(migrationResult) {
                when (migrationResult) {
                    MigrationResult.NotNeeded, is MigrationResult.Success -> {
                        financeSessionProvider.initialize(
                            resources.configuration.locales[0].toLanguageTag(),
                        )
                    }

                    null, is MigrationResult.Failure -> Unit
                }
            }
            DenaroTheme(
                darkTheme = when (preferences.themeMode) {
                    ThemeMode.SYSTEM -> null
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    val session = financeSession
                    var appState: DenaroAppState? = null
                    if (session != null) {
                        appState = rememberDenaroAppState(
                            session = session,
                            defaultCurrency = preferences.defaultCurrency,
                        )
                        DenaroAppPreloader(appState)
                    }
                    AppLockGate(
                        appLockEnabled = securityPreferences.appLockEnabled,
                        processUnlocked = processUnlocked,
                        authenticator = authenticator,
                        processUnlockSession = processUnlockSession,
                    ) {
                        MigrationGate(
                            result = migrationResult,
                            onRetry = migrationViewModel::retry,
                        ) {
                            val currentAppState = appState
                            if (currentAppState == null) {
                                StartupLoading()
                            } else {
                                DenaroApp(
                                    state = currentAppState,
                                    financeSessionProvider = financeSessionProvider,
                                    preferencesRepository = denaroApplication.preferencesRepository,
                                    securityPreferencesRepository = securityPreferencesRepository,
                                    processUnlockSession = processUnlockSession,
                                    authenticator = authenticator,
                                    quickEntryRequest = quickEntryRequest,
                                    onQuickEntryConsumed = ::consumeQuickEntryRequest,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptQuickEntryIntent(intent)
    }

    private fun acceptQuickEntryIntent(intent: Intent?) {
        val action = QuickEntryIntent.parse(intent) ?: return
        quickEntrySequence += 1
        quickEntryRequest = QuickEntryRequest(
            id = SystemClock.elapsedRealtimeNanos() + quickEntrySequence,
            action = action,
        )
    }

    private fun consumeQuickEntryRequest(requestId: Long) {
        if (quickEntryRequest?.id != requestId) return
        quickEntryRequest = null
        setIntent(QuickEntryIntent.clear(intent))
    }

    private fun applyScreenSecurity(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

internal fun shouldKeepSplashVisible(
    migrationResult: MigrationResult?,
    hasFinanceSession: Boolean,
    appLocked: Boolean = false,
): Boolean {
    if (appLocked) return false
    return when (migrationResult) {
        null -> true
        is MigrationResult.Failure -> false
        MigrationResult.NotNeeded, is MigrationResult.Success -> !hasFinanceSession
    }
}

@Composable
private fun MigrationGate(
    result: MigrationResult?,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (val current = result) {
        null -> Unit
        MigrationResult.NotNeeded,
        is MigrationResult.Success,
            -> content()

        is MigrationResult.Failure -> MigrationFailure(
            message = current.message,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun MigrationFailure(
    message: String,
    onRetry: () -> Unit,
) {
    ReportDrawn()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.migration_failed))
        Text(
            text = message,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}
