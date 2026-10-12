package com.example.timetable.data.crypto

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** AES-256-GCM 的真实实现。异常契约见 [CipherEngine]。 */
@Singleton
class KeystoreCipherEngine @Inject constructor() : CipherEngine {

    override fun encrypt(key: SecretKey, aadContext: String, plainText: ByteArray): RawCipherText {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        applyAad(cipher, aadContext)
        val encrypted = cipher.doFinal(plainText)
        return RawCipherText(iv = cipher.iv, cipherText = encrypted)
    }

    override fun decrypt(
        key: SecretKey,
        aadContext: String,
        iv: ByteArray,
        cipherText: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        applyAad(cipher, aadContext)
        return cipher.doFinal(cipherText)
    }

    /** 空 AAD 与「不调用 updateAAD」在 GCM 下等价，因此空串直接跳过。 */
    private fun applyAad(cipher: Cipher, aadContext: String) {
        if (aadContext.isEmpty()) return
        cipher.updateAAD(aadContext.toByteArray(Charsets.UTF_8))
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
