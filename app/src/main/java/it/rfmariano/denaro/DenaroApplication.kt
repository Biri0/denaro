package it.rfmariano.denaro

import android.app.Application
import it.rfmariano.denaro.data.finance.FinanceRepository
import it.rfmariano.denaro.data.local.EncryptedDatabaseFactory

class DenaroApplication : Application() {
    private val database by lazy {
        EncryptedDatabaseFactory(this).open()
    }

    val financeRepository by lazy {
        FinanceRepository(database)
    }
}
