package it.rfmariano.denaro.data.finance

import android.content.Context
import it.rfmariano.denaro.data.backup.DenaroBackupService
import it.rfmariano.denaro.data.local.EncryptedDatabaseFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val recurrenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow<FinanceSession?>(null)

    override val session: StateFlow<FinanceSession?> = _session.asStateFlow()

    override suspend fun initialize(localeTag: String) {
        mutex.withLock {
            if (_session.value != null) return
            val next = withContext(Dispatchers.IO) {
                val database = EncryptedDatabaseFactory(context).open()
                val repository = FinanceRepository(database)
                FinanceSession(
                    id = 1L,
                    repository = repository,
                    isDemo = false,
                    initialDashboardMonth = YearMonth.now().toString(),
                    backupService = DenaroBackupService(
                        database,
                        installedAppVersion(context),
                        context.cacheDir,
                    ),
                )
            }
            _session.value = next
            processStartupRecurrences(next)
        }
    }

    override suspend fun setDemoMode(enabled: Boolean, localeTag: String) = Unit

    override fun clearRecurrenceStartupFailure(sessionId: Long) {
        _session.update { current ->
            if (current?.id == sessionId && current.recurrenceStartupFailed) {
                current.copy(recurrenceStartupFailed = false)
            } else {
                current
            }
        }
    }

    private fun processStartupRecurrences(initialSession: FinanceSession) {
        recurrenceScope.launch {
            try {
                initialSession.repository.processDueRecurrences()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _session.update { current ->
                    if (current?.id == initialSession.id) {
                        current.copy(recurrenceStartupFailed = true)
                    } else {
                        current
                    }
                }
            }
        }
    }
}
