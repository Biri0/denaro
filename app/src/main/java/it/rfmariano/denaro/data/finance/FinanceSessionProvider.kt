package it.rfmariano.denaro.data.finance

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

data class FinanceSession(
    val id: Long,
    val repository: FinanceRepository,
    val isDemo: Boolean,
    val initialDashboardMonth: String,
)

interface FinanceSessionProvider {
    val session: StateFlow<FinanceSession?>

    suspend fun initialize(localeTag: String)

    suspend fun setDemoMode(enabled: Boolean, localeTag: String)
}

fun createFinanceSessionProvider(context: Context): FinanceSessionProvider =
    createBuildFinanceSessionProvider(context)
