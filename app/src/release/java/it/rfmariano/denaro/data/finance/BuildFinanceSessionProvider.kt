package it.rfmariano.denaro.data.finance

import android.content.Context
import it.rfmariano.denaro.data.local.EncryptedDatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.YearMonth

internal fun createBuildFinanceSessionProvider(context: Context): FinanceSessionProvider =
    ReleaseFinanceSessionProvider(context)

private class ReleaseFinanceSessionProvider(
    private val context: Context,
) : FinanceSessionProvider {
    private val mutex = Mutex()
    private val _session = MutableStateFlow<FinanceSession?>(null)

    override val session: StateFlow<FinanceSession?> = _session.asStateFlow()

    override suspend fun initialize(localeTag: String) {
        mutex.withLock {
            if (_session.value != null) return
            _session.value = withContext(Dispatchers.IO) {
                FinanceSession(
                    id = 1L,
                    repository = FinanceRepository(EncryptedDatabaseFactory(context).open()),
                    isDemo = false,
                    initialDashboardMonth = YearMonth.now().toString(),
                )
            }
        }
    }

    override suspend fun setDemoMode(enabled: Boolean, localeTag: String) = Unit
}
