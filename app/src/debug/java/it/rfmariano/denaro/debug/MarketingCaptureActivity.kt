package it.rfmariano.denaro.debug

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.rfmariano.denaro.DenaroApplication
import it.rfmariano.denaro.R
import it.rfmariano.denaro.data.finance.DebugFinanceSessionProvider
import it.rfmariano.denaro.data.finance.FinanceSession
import it.rfmariano.denaro.ui.AccountDetailRouteContent
import it.rfmariano.denaro.ui.AccountDetailViewModel
import it.rfmariano.denaro.ui.AccountsRouteContent
import it.rfmariano.denaro.ui.AccountsViewModel
import it.rfmariano.denaro.ui.ActivityRouteContent
import it.rfmariano.denaro.ui.ActivityViewModel
import it.rfmariano.denaro.ui.CategoryManagementScreen
import it.rfmariano.denaro.ui.DebtsViewModel
import it.rfmariano.denaro.ui.HomeRouteContent
import it.rfmariano.denaro.ui.HomeViewModel
import it.rfmariano.denaro.ui.theme.DenaroTheme
import it.rfmariano.denaro.ui.viewModelFactory
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import com.composables.icons.lucide.R as LucideR

class MarketingCaptureActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val localeTag = intent?.getStringExtra(EXTRA_LOCALE) ?: "en"
        val localized = newBase.createConfigurationContext(
            Configuration(newBase.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(localeTag))
            },
        )
        super.attachBaseContext(localized)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val localeTag = intent.getStringExtra(EXTRA_LOCALE) ?: "en"
        super.onCreate(savedInstanceState)

        WindowCompat.getInsetsController(window, window.decorView).hide(
            WindowInsetsCompat.Type.systemBars(),
        )
        val scenario = CaptureScenario.from(intent.getStringExtra(EXTRA_SCENARIO))
        val darkTheme = intent.getStringExtra(EXTRA_THEME) == "dark"
        val referenceDate = intent.getStringExtra(EXTRA_REFERENCE_DATE)
            ?.let(LocalDate::parse)
            ?: LocalDate.now()
        val provider = (application as DenaroApplication).financeSessionProvider
                as DebugFinanceSessionProvider

        lifecycleScope.launch {
            val session = provider.prepareCapture(localeTag, referenceDate)
            setContent {
                DenaroTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        CaptureContent(session, scenario)
                    }
                }
            }
        }
    }

    private companion object {
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_THEME = "theme"
        const val EXTRA_LOCALE = "locale"
        const val EXTRA_REFERENCE_DATE = "reference_date"
    }
}

private enum class CaptureScenario {
    HOME,
    ACCOUNTS,
    ACCOUNT_DETAIL,
    ACTIVITY,
    DEBTS,
    CATEGORIES;

    companion object {
        fun from(value: String?): CaptureScenario = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: HOME
    }
}

private enum class CaptureDestination(val label: Int, val icon: Int) {
    HOME(R.string.home, LucideR.drawable.lucide_ic_house),
    ACCOUNTS(R.string.accounts, LucideR.drawable.lucide_ic_wallet),
    ACTIVITY(R.string.activity, LucideR.drawable.lucide_ic_list),
}

@Composable
private fun CaptureContent(session: FinanceSession, scenario: CaptureScenario) {
    val repository = session.repository
    when (scenario) {
        CaptureScenario.HOME -> CaptureScaffold(CaptureDestination.HOME) {
            val home: HomeViewModel = viewModel(
                key = "capture-home-${session.id}",
                factory = viewModelFactory {
                    HomeViewModel(repository, session.initialDashboardMonth)
                },
            )
            HomeRouteContent(
                viewModel = home,
                amountsVisible = true,
                onToggleAmounts = {},
                onAddAccount = {},
                onSettings = {},
                onCategoryClick = { _, _, _, _, _, _ -> },
            )
        }

        CaptureScenario.ACCOUNTS -> CaptureScaffold(CaptureDestination.ACCOUNTS) {
            val accounts: AccountsViewModel = viewModel(
                key = "capture-accounts-${session.id}",
                factory = viewModelFactory { AccountsViewModel(repository) },
            )
            AccountsRouteContent(
                viewModel = accounts,
                amountsVisible = true,
                onAccountClick = {},
                onAddAccount = {},
                onArchivedAccounts = {},
            )
        }

        CaptureScenario.ACCOUNT_DETAIL -> CaptureScaffold(CaptureDestination.ACCOUNTS) {
            val detail: AccountDetailViewModel = viewModel(
                key = "capture-detail-${session.id}",
                factory = viewModelFactory {
                    AccountDetailViewModel(repository, "demo-account-checking")
                },
            )
            AccountDetailRouteContent(
                viewModel = detail,
                amountsVisible = true,
                onBack = {},
                onEdit = {},
                onEditSchedule = {},
                onMessage = {},
            )
        }

        CaptureScenario.ACTIVITY,
        CaptureScenario.DEBTS,
        -> CaptureScaffold(CaptureDestination.ACTIVITY) {
            val activity: ActivityViewModel = viewModel(
                key = "capture-activity-${session.id}",
                factory = viewModelFactory { ActivityViewModel(repository) },
            )
            val debts: DebtsViewModel = viewModel(
                key = "capture-debts-${session.id}",
                factory = viewModelFactory { DebtsViewModel(repository) },
            )
            val debtsState by debts.uiState.collectAsStateWithLifecycle()
            ActivityRouteContent(
                viewModel = activity,
                amountsVisible = true,
                onAddActivity = {},
                onActivityClick = {},
                debts = debtsState.debts,
                initiallyShowDebts = scenario == CaptureScenario.DEBTS,
            )
        }

        CaptureScenario.CATEGORIES -> CategoryManagementScreen(
            repository = repository,
            onBack = {},
            onEdit = { _, _, _ -> },
        )
    }
}

@Composable
private fun CaptureScaffold(
    selected: CaptureDestination,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            CaptureDestination.entries.forEach { destination ->
                item(
                    selected = destination == selected,
                    onClick = {},
                    icon = {
                        Icon(
                            painterResource(destination.icon),
                            contentDescription = stringResource(destination.label),
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
        content = content,
    )
}
