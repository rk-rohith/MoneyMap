package com.moneymap.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The optional backup password, kept so weekly backups can be encrypted without asking.
 * It is stored encrypted with a key that never leaves the Android Keystore. Reinstalling the app loses that key,
 * so the person must remember the password to restore a backup on a new install.
 */
object BackupPassword {
    private const val PREFS = "backup_password"
    private const val KEY_VALUE = "value"
    private const val ALIAS = "moneymap_backup_password"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSet(context: Context): Boolean = prefs(context).contains(KEY_VALUE)

    fun get(context: Context): String? {
        val stored = prefs(context).getString(KEY_VALUE, null) ?: return null
        return runCatching {
            val (iv, data) = stored.split(':').let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.doFinal(data).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    /** Saves [password], or removes it when null/blank. */
    fun set(context: Context, password: String?) {
        if (password.isNullOrBlank()) {
            prefs(context).edit().remove(KEY_VALUE).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
        prefs(context).edit().putString(KEY_VALUE, value).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
