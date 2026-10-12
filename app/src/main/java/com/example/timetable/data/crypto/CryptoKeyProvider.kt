package com.example.timetable.data.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主密钥宿主：Android Keystore 生成不可导出的 AES-256-GCM 密钥。
 * 生成时优先要求 StrongBox（安全芯片内完成密钥运算），设备不支持则回落到普通 Keystore。
 *
 * SQLCipher 口令由随机 32 字节生成后用该密钥包裹落盘 —— 那部分的密文布局与 AAD
 * **刻意保持骨架期原样**：改布局会让已落盘的口令解不开，整库打不开。
 */
@Singleton
class CryptoKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: KeyStore,
    private val secureRandom: SecureRandom,
    private val secretKeyGenerator: SecretKeyGenerator,
) : MasterKeySource {

    /** @throws SecurePrefsError.KeyMissing 主密钥既取不到也生成不出来。 */
    override fun secretKey(): SecretKey {
        existingSecretKey()?.let { return it }
        return generateSecretKey()
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

    private fun existingSecretKey(): SecretKey? = try {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (error: GeneralSecurityException) {
        // 条目存在但不可读时按「不存在」处理，交给生成路径；
        // 若别名已被占用导致生成失败，最终会以 KeyMissing 收口，不会静默返回坏密钥。
        Log.w(TAG, "主密钥条目不可读，按不存在处理", error)
        null
    }

    private fun generateSecretKey(): SecretKey = try {
        StrongBoxKeyFactory.generate(
            generator = secretKeyGenerator,
            alias = KEY_ALIAS,
            deleteAlias = { alias -> runCatching { keyStore.deleteEntry(alias) } },
            onStrongBoxUnavailable = { error ->
                Log.w(TAG, "StrongBox 不可用，回落到普通 Keystore", error)
            },
        )
    } catch (error: ProviderException) {
        Log.e(TAG, "主密钥生成失败：StrongBox 与普通 Keystore 都不可用", error)
        throw SecurePrefsError.KeyMissing
    } catch (error: GeneralSecurityException) {
        Log.e(TAG, "主密钥生成失败", error)
        throw SecurePrefsError.KeyMissing
    }

    /**
     * 骨架期布局：iv || cipherText，无版本前缀、无 AAD。
     * **不要改动** —— 已有口令是按这个布局落盘的，改了就打不开数据库。
     */
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
        /** 主密钥别名。public 是为了让 instrumentation 测试能在用例间清理它。 */
        const val KEY_ALIAS = "timetable_master_key"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val PASSPHRASE_BYTES = 32
        private const val PREFS_NAME = "timetable_secure_key"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val TAG = "CryptoKeyProvider"
    }
}
