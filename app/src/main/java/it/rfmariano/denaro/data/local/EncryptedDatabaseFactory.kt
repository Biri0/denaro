package it.rfmariano.denaro.data.local

import android.content.Context
import androidx.room.Room
import it.rfmariano.denaro.data.security.DatabaseKeyManager
import it.rfmariano.denaro.data.security.SqlCipherLoader
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class EncryptedDatabaseFactory(
    private val context: Context,
    private val keyManager: DatabaseKeyManager = DatabaseKeyManager(context),
) {
    fun open(): DenaroDatabase {
        SqlCipherLoader.load()
        val factory = SupportOpenHelperFactory(keyManager.getOrCreatePassphrase())
        return Room.databaseBuilder(
            context.applicationContext,
            DenaroDatabase::class.java,
            DATABASE_NAME,
        )
            .openHelperFactory(factory)
            .build()
    }

    fun deleteDatabaseAndKey() {
        context.deleteDatabase(DATABASE_NAME)
        keyManager.deleteKeyMaterial()
    }

    companion object {
        const val DATABASE_NAME = "denaro_native.db"
    }
}
