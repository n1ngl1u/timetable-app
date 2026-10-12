package com.example.timetable.data.crypto

import java.security.GeneralSecurityException
import javax.crypto.SecretKey

/** 一次加密的原始产物：IV 与密文分开，由信封层负责加前缀与编码。 */
class RawCipherText(
    val iv: ByteArray,
    val cipherText: ByteArray,
)

/**
 * 真实 Cipher 调用的唯一接触面。抽成接口是为了让 [FieldCipher] 的信封与错误映射逻辑
 * 能在 JVM 上被测 —— AndroidKeyStore 是 native provider，JVM 与 Robolectric 都模拟不了。
 *
 * 异常契约（实现必须遵守）：
 * - [encrypt] / [decrypt] 只允许抛 [GeneralSecurityException] 及其子类，或 [IllegalArgumentException]；
 * - tag 校验失败或 AAD 不匹配必须抛 javax.crypto.AEADBadTagException；
 * - 不得抛其他 RuntimeException，也不得吞掉异常后返回空数组。
 */
interface CipherEngine {

    fun encrypt(key: SecretKey, aadContext: String, plainText: ByteArray): RawCipherText

    fun decrypt(
        key: SecretKey,
        aadContext: String,
        iv: ByteArray,
        cipherText: ByteArray,
    ): ByteArray
}
