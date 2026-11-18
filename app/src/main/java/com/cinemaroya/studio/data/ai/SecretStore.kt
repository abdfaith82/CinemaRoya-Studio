package com.cinemaroya.studio.data.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts API keys at rest with an AES-256 key held in Android Keystore (the key never leaves the device).
 * Old plain values are read as-is and re-encrypted on the next save.
 */
object SecretStore {
    private const val ALIAS = "cinemaroya_secret_v1"
    const val PREFIX = "enc1:"

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

    /** If Keystore fails (rare devices) the text is returned unchanged rather than losing the key. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty() || plain.startsWith(PREFIX)) return plain
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            PREFIX + Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }.getOrDefault(plain)
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching {
            val all = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
            String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
