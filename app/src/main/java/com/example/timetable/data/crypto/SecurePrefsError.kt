package com.example.timetable.data.crypto

/**
 * 敏感存储的四级错误。刻意继承 Exception 而不是返回裸布尔：
 * 调用方必须能区分「没存过」「密钥没了」「被人动过」「版本不认识」。
 *
 * 注意 KeyMissing 的边界：Keystore 主密钥被系统重置后，旧密文解密会得到
 * AEADBadTagException，这在密码学上与「密文被篡改」无法区分，因此归 IntegrityViolation。
 * 要区分「密钥轮换」与「篡改」需要额外存一份密钥指纹，本批不含该机制。
 */
sealed class SecurePrefsError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * Keystore 主密钥既取不到、也生成不出来。
     * 根因（Keystore 抛出的原始异常）只写进日志，不进 cause —— 保持 data object 的可比较性，
     * 让 [SecurePrefsResult.Failure] 的相等断言在测试里可用。
     */
    data object KeyMissing : SecurePrefsError("Keystore 主密钥既取不到也生成不出来")

    /** GCM tag 校验失败、AAD 不匹配，或密文载荷结构损坏。 */
    data class IntegrityViolation(
        val reason: String,
        val rootCause: Throwable? = null,
    ) : SecurePrefsError("密文完整性校验失败：$reason", rootCause)

    /** 版本前缀能识别，但不是当前支持的版本。 */
    data class UnsupportedFormat(val prefix: String) :
        SecurePrefsError("不认识的密文版本前缀：$prefix")

    /** 其余不可恢复错误。 */
    data class Unrecoverable(val rootCause: Throwable?) :
        SecurePrefsError("不可恢复的敏感存储错误", rootCause)
}
