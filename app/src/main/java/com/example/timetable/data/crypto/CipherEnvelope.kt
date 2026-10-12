package com.example.timetable.data.crypto

import java.util.Base64

/**
 * 密文载荷的版本前缀与分帧。纯字符串与字节处理，无 Android 依赖，可 JVM 单测。
 *
 * 格式：
 * 旧（骨架期）  base64(iv) : base64(cipherText)           —— 无版本前缀、空 AAD
 * v1            v1: base64(iv) : base64(cipherText)
 *
 * 先判前缀再分帧：旧格式的 base64 IV 恒为 16 字符且不含冒号，
 * 三字符的 "v1:" 不会与它碰撞。
 */
object CipherEnvelope {

    const val VERSION_V1 = "v1"
    const val VERSION_PREFIX_V1 = "v1:"
    const val SEPARATOR = ":"

    /** GCM 标准 IV 长度；16 用于兼容历史上可能出现的更长 IV。 */
    val ALLOWED_IV_BYTES = setOf(12, 16)

    /** GCM tag 本身 16 字节，密文短于此长度必然不是合法载荷。 */
    const val MIN_CIPHER_TEXT_BYTES = 16

    private val VERSION_PATTERN = Regex("^v(\\d+):")

    sealed interface Parsed {

        data class V1(val iv: ByteArray, val cipherText: ByteArray) : Parsed

        data class Legacy(val iv: ByteArray, val cipherText: ByteArray) : Parsed

        /** 结构损坏：base64 非法、段数不对、长度越界。 */
        data class Invalid(val reason: String) : Parsed

        /** 版本前缀能识别但不是当前支持的版本。 */
        data class Unsupported(val prefix: String) : Parsed
    }

    fun encode(iv: ByteArray, cipherText: ByteArray): String {
        val encoder = Base64.getEncoder()
        return VERSION_PREFIX_V1 +
            encoder.encodeToString(iv) +
            SEPARATOR +
            encoder.encodeToString(cipherText)
    }

    /** 该载荷是否已经带版本前缀。用于「已迁移过就不再重写」的判断。 */
    fun isVersioned(payload: String): Boolean = payload.startsWith(VERSION_PREFIX_V1)

    fun parse(payload: String): Parsed {
        val versionMatch = VERSION_PATTERN.find(payload)
        if (versionMatch != null) {
            val version = versionMatch.groupValues[1]
            if (version != "1") {
                return Parsed.Unsupported(versionMatch.value)
            }
            return decodeFrame(payload.removePrefix(VERSION_PREFIX_V1), versioned = true)
        }
        return decodeFrame(payload, versioned = false)
    }

    private fun decodeFrame(frame: String, versioned: Boolean): Parsed {
        val parts = frame.split(SEPARATOR)
        if (parts.size != 2) {
            return Parsed.Invalid("载荷应由两段组成，实际 " + parts.size + " 段")
        }
        val iv = decodeBase64(parts[0]) ?: return Parsed.Invalid("IV 不是合法 base64")
        val cipherText = decodeBase64(parts[1]) ?: return Parsed.Invalid("密文不是合法 base64")

        if (!ALLOWED_IV_BYTES.contains(iv.size)) {
            return Parsed.Invalid("IV 长度非法：" + iv.size + " 字节")
        }
        if (cipherText.size < MIN_CIPHER_TEXT_BYTES) {
            return Parsed.Invalid("密文长度非法：" + cipherText.size + " 字节，至少需要 " + MIN_CIPHER_TEXT_BYTES)
        }
        return if (versioned) Parsed.V1(iv, cipherText) else Parsed.Legacy(iv, cipherText)
    }

    private fun decodeBase64(value: String): ByteArray? = try {
        Base64.getDecoder().decode(value)
    } catch (error: IllegalArgumentException) {
        null
    }
}
