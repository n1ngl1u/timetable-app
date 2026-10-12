package com.example.timetable.core.common

/**
 * 全应用统一错误体系。UI 层只消费该类型，不直接暴露底层异常。
 */
sealed class AppError {
    abstract val message: String
    abstract val cause: Throwable?

    data class Network(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()

    data class Unauthorized(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()

    data class SchemaChanged(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()

    data class MissingField(
        override val message: String,
        val field: String,
    ) : AppError() {
        override val cause: Throwable? = null
    }

    data class Storage(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()

    data class Crypto(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()

    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError()
}
