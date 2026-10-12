package com.example.timetable.testing

import com.example.timetable.data.crypto.CipherEngine
import com.example.timetable.data.crypto.MasterKeySource
import com.example.timetable.data.crypto.RawCipherText
import com.example.timetable.data.crypto.SecretKeyGenerator
import com.example.timetable.data.crypto.SecureKeyValueStore
import com.example.timetable.data.crypto.SecurePrefsError
import java.security.ProviderException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 四个独立替身，刻意不合并：合并后会出现在测 AAD 时被迫构造 Keystore 状态这类耦合。
 * 全部只依赖 JVM 可用的类型（SecretKey / ProviderException / Base64），
 * 因为 AndroidKeyStore 在 JVM 与 Robolectric 下都不可用。
 */

/**
 * 记录每次加密用的 AAD 与密文，解密时逐项比对。
 * **不忽略 AAD**：忽略的话「AAD 不匹配 → IntegrityViolation」这条测不出来。
 */
class FakeCipherEngine : CipherEngine {

    private class Recorded(val aadContext: String, val cipherText: ByteArray)

    private val recorded = mutableMapOf<String, Recorded>()

    private var ivCounter = 0

    /** 按加密顺序记录的 AAD，供断言「AAD 取到记录级」。 */
    val aadContexts = mutableListOf<String>()

    /** 只让下一次加密失败，用完即清。 */
    var failNextEncryptWith: Throwable? = null

    /** 让每次加密都失败。 */
    var failEncryptWith: Throwable? = null

    var encryptCount = 0
        private set

    override fun encrypt(key: SecretKey, aadContext: String, plainText: ByteArray): RawCipherText {
        failNextEncryptWith?.let { pending ->
            failNextEncryptWith = null
            throw pending
        }
        failEncryptWith?.let { throw it }

        encryptCount++
        aadContexts += aadContext
        val iv = ByteArray(IV_BYTES).also { bytes -> bytes[0] = (ivCounter++).toByte() }
        // 可逆但非加密的变换：这里测的是信封与错误映射，不是密码学强度。
        // 必须补上 16 字节 tag —— 真实 GCM 密文恒比明文长一个 tag，
        // 不带 tag 的假密文会被 CipherEnvelope 的最短长度校验挡下，测不到解密路径。
        val body = ByteArray(plainText.size) { index -> (plainText[index] + 1).toByte() }
        val cipherText = body + ByteArray(TAG_BYTES) { index -> (index + 0x40).toByte() }
        recorded[ivKey(iv)] = Recorded(aadContext, cipherText)
        return RawCipherText(iv = iv, cipherText = cipherText)
    }

    override fun decrypt(
        key: SecretKey,
        aadContext: String,
        iv: ByteArray,
        cipherText: ByteArray,
    ): ByteArray {
        val entry = recorded[ivKey(iv)] ?: throw AEADBadTagException("IV 未知")
        if (!cipherText.contentEquals(entry.cipherText)) {
            throw AEADBadTagException("密文被篡改")
        }
        if (entry.aadContext != aadContext) {
            throw AEADBadTagException("AAD 不匹配")
        }
        // 剥掉 tag：真实实现返回的明文不含 tag
        val body = cipherText.copyOfRange(0, cipherText.size - TAG_BYTES)
        return ByteArray(body.size) { index -> (body[index] - 1).toByte() }
    }

    private fun ivKey(iv: ByteArray): String = Base64.getEncoder().encodeToString(iv)

    private companion object {
        const val IV_BYTES = 12

        /** 与真实 GCM 一致的 tag 长度。 */
        const val TAG_BYTES = 16
    }
}

/** 主密钥来源替身。failure 非空时按配置抛出，用于覆盖 KeyMissing。 */
class FakeMasterKeySource : MasterKeySource {

    private val key: SecretKey = SecretKeySpec(ByteArray(32) { 7 }, "AES")

    var failure: SecurePrefsError? = null

    override fun secretKey(): SecretKey {
        failure?.let { throw it }
        return key
    }
}

/** StrongBox 钩子替身。默认「StrongBox 不可用」，用于覆盖回落分支。 */
class FakeSecretKeyGenerator : SecretKeyGenerator {

    /** 每次调用记录的 strongBoxRequired，回落后应为 [true, false]。 */
    val requestedStrongBox = mutableListOf<Boolean>()

    var strongBoxFailure: Throwable? = ProviderException("StrongBox 不可用")

    var alwaysFailWith: Throwable? = null

    override fun generate(alias: String, strongBoxRequired: Boolean): SecretKey {
        requestedStrongBox += strongBoxRequired
        if (strongBoxRequired) {
            strongBoxFailure?.let { throw it }
        }
        alwaysFailWith?.let { throw it }
        return SecretKeySpec(ByteArray(32) { 3 }, "AES")
    }
}

/** 内存键值层替身，可配置写入/删除/清空失败。 */
class InMemoryKeyValueStore : SecureKeyValueStore {

    private val values = linkedMapOf<String, String>()

    var failOnPut: Throwable? = null

    var failOnRemove: Throwable? = null

    var failOnClear: Throwable? = null

    fun snapshot(): Map<String, String> = values.toMap()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        failOnPut?.let { throw it }
        values[key] = value
    }

    override fun remove(key: String) {
        failOnRemove?.let { throw it }
        values.remove(key)
    }

    override fun clear() {
        failOnClear?.let { throw it }
        values.clear()
    }

    override fun keys(): Set<String> = values.keys.toSet()
}
