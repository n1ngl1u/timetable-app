package com.example.timetable.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主密钥宿主：Android Keystore 生成不可导出的 AES-256-GCM 密钥，
 * SQLCipher 口令由随机 32 字节生成后用该密钥包裹落盘。
 */
@Singleton
class CryptoKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: KeyStore,
    private val secureRandom: SecureRandom,
) {

    fun secretKey(): SecretKey {
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: generateSecretKey()
    }

    fun databasePassphrase(): ByteArray {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) {
            return unwrap(Base64.decode(stored, Base64.NO_WRAP))
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES)
        secureRandom.nextBytes(passphrase)
        preferences.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(wrap(passphrase), Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private fun generateSecretKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun unwrap(payload: ByteArray): ByteArray {
        require(payload.size > IV_BYTES) { "加密载荷长度非法" }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val encrypted = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "timetable_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val PASSPHRASE_BYTES = 32
        private const val PREFS_NAME = "timetable_secure_key"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
    }
}
