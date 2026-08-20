package it.rfmariano.denaro.data.local

import android.content.Context
import androidx.room.Room
import it.rfmariano.denaro.data.security.DatabaseKeyManager
import it.rfmariano.denaro.data.security.SqlCipherLoader
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class EncryptedDatabaseFactory(
    private val context: Context,
    private val keyManager: DatabaseKeyManager = DatabaseKeyManager(context),
    private val databaseName: String = DATABASE_NAME,
) {
    fun open(): DenaroDatabase {
        SqlCipherLoader.load()
        val factory = SupportOpenHelperFactory(keyManager.getOrCreatePassphrase())
        return Room.databaseBuilder(
            context.applicationContext,
            DenaroDatabase::class.java,
            databaseName,
        )
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    fun deleteDatabaseAndKey() {
        context.deleteDatabase(databaseName)
        keyManager.deleteKeyMaterial()
    }

    companion object {
        const val DATABASE_NAME = "denaro_native.db"
    }
}
