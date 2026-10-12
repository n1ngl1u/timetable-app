package com.example.timetable.data.crypto

/**
 * 敏感存储的结果类型。不用 Kotlin 的 Result<T>：
 * 后者的失败分支只能装 Throwable，取回 [SecurePrefsError] 需要强转，
 * 而这里每一次失败都必须携带确定类型的错误。
 */
sealed interface SecurePrefsResult<out T> {

    data class Success<T>(val value: T) : SecurePrefsResult<T>

    data class Failure(val error: SecurePrefsError) : SecurePrefsResult<Nothing>
}

/** 成功取值，失败与「值为 null」都返回 null —— 只用于不关心错误原因的调用方。 */
fun <T> SecurePrefsResult<T>.valueOrNull(): T? =
    (this as? SecurePrefsResult.Success<T>)?.value

fun <T> SecurePrefsResult<T>.errorOrNull(): SecurePrefsError? =
    (this as? SecurePrefsResult.Failure)?.error
