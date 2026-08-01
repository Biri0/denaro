package it.rfmariano.denaro

import android.app.Application
import it.rfmariano.denaro.data.finance.createFinanceSessionProvider
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository

class DenaroApplication : Application() {
    val preferencesRepository by lazy {
        AppPreferencesRepository(this)
    }

    val financeSessionProvider by lazy {
        createFinanceSessionProvider(this)
    }

    override fun onCreate() {
        super.onCreate()
        preferencesRepository.applyStoredTheme()
    }
}
