package it.rfmariano.denaro.data.migration

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class LegacySecureStoreKeyReader(private val context: Context) {
    fun hasDatabaseKeyEntries(): Boolean {
        val preferences = context.getSharedPreferences(
            SHARED_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        return CURRENT_KEYS.any(preferences::contains) ||
                preferences.contains(LEGACY_KEY_VERSION) ||
                preferences.contains("db_key_version")
    }

    fun readDatabasePassphrase(): String {
        val preferences = context.getSharedPreferences(
            SHARED_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val encoded = CURRENT_KEYS.firstNotNullOfOrNull {
            preferences.getString(it, null)
        }
            ?: throw LegacyMigrationException("Legacy database key was not found")
        val item = JSONObject(encoded)
        check(item.getString("scheme") == "aes") {
            "Unsupported Expo SecureStore encryption scheme"
        }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val usesSuffix = item.optBoolean("usesKeystoreSuffix", false)
        val alias = if (usesSuffix) {
            "$BASE_KEYSTORE_ALIAS:$UNAUTHENTICATED_SUFFIX"
        } else {
            BASE_KEYSTORE_ALIAS
        }
        val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            ?: throw LegacyMigrationException("Legacy Keystore key was not found")

        val iv = Base64.decode(item.getString("iv"), Base64.DEFAULT)
        val tagLength = item.getInt("tlen")
        val ciphertext = Base64.decode(item.getString("ct"), Base64.DEFAULT)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(
            Cipher.DECRYPT_MODE,
            entry.secretKey,
            GCMParameterSpec(tagLength, iv),
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun deleteDatabaseKeyEntries() {
        val deleted = context
            .getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                CURRENT_KEYS.forEach(::remove)
                remove(LEGACY_KEY_VERSION)
                remove("db_key_version")
            }
            .commit()
        if (!deleted) {
            throw LegacyMigrationException("Could not delete the legacy database key")
        }
    }

    private companion object {
        const val SHARED_PREFERENCES_NAME = "SecureStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CIPHER = "AES/GCM/NoPadding"
        const val BASE_KEYSTORE_ALIAS = "$CIPHER:key_v1"
        const val UNAUTHENTICATED_SUFFIX = "keystoreUnauthenticated"
        const val LEGACY_KEY_VERSION = "key_v1-db_key_version"
        val CURRENT_KEYS = listOf(
            "key_v1-database_encryption_key",
            "database_encryption_key",
        )
    }
}
