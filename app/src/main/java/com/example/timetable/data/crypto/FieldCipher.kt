package com.example.timetable.data.crypto

import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/** 解密结果。legacyPayload = true 表示这条密文是 v1 之前的格式，调用方应重新加密写回。 */
sealed interface FieldDecryptionResult {

    data class Success(val plainText: String, val legacyPayload: Boolean) : FieldDecryptionResult

    data class Failure(val error: SecurePrefsError) : FieldDecryptionResult
}

/**
 * 字段级 AES-256-GCM 加解密，输出带版本前缀的载荷：v1:base64(iv):base64(cipherText)。
 *
 * AAD 绑定：aadContext 作为 AES-GCM 的附加认证数据。tag 只保证密文没被改过，
 * 不保证「这段密文属于这个字段/这条记录」；绑定后把别的记录密文搬过来会解密失败。
 * 默认空串只服务于读取 v1 之前的旧密文。
 */
@Singleton
class FieldCipher @Inject constructor(
    private val masterKeySource: MasterKeySource,
    private val engine: CipherEngine,
) {

    /** @throws SecurePrefsError 主密钥不可用时抛出。写路径没有「降级读」的语义，因此直接抛。 */
    fun encrypt(plainText: String, aadContext: String = ""): String {
        val raw = engine.encrypt(
            key = masterKeySource.secretKey(),
            aadContext = aadContext,
            plainText = plainText.toByteArray(Charsets.UTF_8),
        )
        return CipherEnvelope.encode(raw.iv, raw.cipherText)
    }

    fun decrypt(payload: String, aadContext: String = ""): FieldDecryptionResult {
        val frame = when (val parsed = CipherEnvelope.parse(payload)) {
            is CipherEnvelope.Parsed.Unsupported ->
                return FieldDecryptionResult.Failure(SecurePrefsError.UnsupportedFormat(parsed.prefix))
            is CipherEnvelope.Parsed.Invalid ->
                return FieldDecryptionResult.Failure(
                    SecurePrefsError.IntegrityViolation(parsed.reason),
                )
            is CipherEnvelope.Parsed.Legacy ->
                Triple(parsed.iv, parsed.cipherText, true)
            is CipherEnvelope.Parsed.V1 ->
                Triple(parsed.iv, parsed.cipherText, false)
        }
        val (iv, cipherText, legacy) = frame
        // 旧密文一律用空 AAD 解密：骨架期没有 AAD 概念
        val effectiveAad = if (legacy) LEGACY_AAD_CONTEXT else aadContext

        return try {
            val plain = engine.decrypt(
                key = masterKeySource.secretKey(),
                aadContext = effectiveAad,
                iv = iv,
                cipherText = cipherText,
            )
            FieldDecryptionResult.Success(String(plain, Charsets.UTF_8), legacyPayload = legacy)
        } catch (error: SecurePrefsError) {
            FieldDecryptionResult.Failure(error)
        } catch (error: AEADBadTagException) {
            FieldDecryptionResult.Failure(
                SecurePrefsError.IntegrityViolation("GCM tag 校验失败或 AAD 不匹配", error),
            )
        } catch (error: GeneralSecurityException) {
            FieldDecryptionResult.Failure(SecurePrefsError.Unrecoverable(error))
        } catch (error: IllegalArgumentException) {
            FieldDecryptionResult.Failure(
                SecurePrefsError.IntegrityViolation("密文参数非法", error),
            )
        }
    }

    internal companion object {
        /** 旧密文的 AAD：空串即「加密时未调用 updateAAD」。 */
        const val LEGACY_AAD_CONTEXT = ""
    }
}
