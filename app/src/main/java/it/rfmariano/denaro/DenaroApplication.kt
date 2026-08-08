package it.rfmariano.denaro

import android.app.Application
import it.rfmariano.denaro.data.finance.createFinanceSessionProvider
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository
import it.rfmariano.denaro.data.security.ProcessUnlockSession
import it.rfmariano.denaro.data.security.SecurityPreferencesRepository

class DenaroApplication : Application() {
    val preferencesRepository by lazy {
        AppPreferencesRepository(this)
    }

    val financeSessionProvider by lazy {
        createFinanceSessionProvider(this)
    }

    val securityPreferencesRepository by lazy {
        SecurityPreferencesRepository(this)
    }

    val processUnlockSession by lazy {
        ProcessUnlockSession(
            securityPreferencesRepository.state.value.appLockEnabled,
        )
    }

    override fun onCreate() {
        super.onCreate()
        preferencesRepository.applyStoredTheme()
    }
}
