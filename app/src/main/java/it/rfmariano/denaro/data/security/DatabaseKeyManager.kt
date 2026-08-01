package it.rfmariano.denaro.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

open class DatabaseKeyManager(context: Context) {
    private val envelopeFile = AtomicFile(
        File(ensureNoBackupDirectory(context), KEY_ENVELOPE_FILE),
    )

    open fun getOrCreatePassphrase(): ByteArray {
        val wrappingKey = getOrCreateWrappingKey()
        if (envelopeFile.baseFile.exists()) {
            return decryptEnvelope(wrappingKey)
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        writeEnvelope(wrappingKey, passphrase)
        return passphrase
    }

    open fun deleteKeyMaterial() {
        envelopeFile.delete()
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keyStore.deleteEntry(KEYSTORE_ALIAS)
        }
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun writeEnvelope(key: SecretKey, passphrase: ByteArray) {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val json = JSONObject()
            .put("version", 1)
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put(
                "ciphertext",
                Base64.encodeToString(cipher.doFinal(passphrase), Base64.NO_WRAP),
            )

        val output = envelopeFile.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            envelopeFile.finishWrite(output)
        } catch (error: Exception) {
            envelopeFile.failWrite(output)
            throw error
        }
    }

    private fun decryptEnvelope(key: SecretKey): ByteArray {
        val json = JSONObject(
            envelopeFile.openRead().bufferedReader().use { it.readText() },
        )
        check(json.getInt("version") == 1) { "Unsupported database key envelope" }

        val cipher = Cipher.getInstance(CIPHER)
        val iv = Base64.decode(json.getString("iv"), Base64.DEFAULT)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(
            Base64.decode(json.getString("ciphertext"), Base64.DEFAULT),
        )
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        fun ensureNoBackupDirectory(context: Context): File =
            context.noBackupFilesDir.also { directory ->
                check(directory.isDirectory || directory.mkdirs()) {
                    "Could not create the no-backup directory"
                }
            }

        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CIPHER = "AES/GCM/NoPadding"
        const val KEYSTORE_ALIAS = "denaro_native_database_key_v1"
        const val KEY_ENVELOPE_FILE = "denaro_native_database_key.json"
        const val PASSPHRASE_BYTES = 32
    }
}
