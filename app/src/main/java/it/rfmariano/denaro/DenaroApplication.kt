package it.rfmariano.denaro

import android.app.Application
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.local.EncryptedDatabaseFactory
import it.rfmariano.denaro.data.preferences.AppPreferencesRepository

class DenaroApplication : Application() {
    val preferencesRepository by lazy {
        AppPreferencesRepository(this)
    }

    private val database by lazy {
        EncryptedDatabaseFactory(this).open()
    }

    val financeRepository by lazy {
        FinanceRepository(database)
    }

    override fun onCreate() {
        super.onCreate()
        preferencesRepository.applyStoredTheme()
    }
}
