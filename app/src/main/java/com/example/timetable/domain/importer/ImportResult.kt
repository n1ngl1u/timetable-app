package com.example.timetable.domain.importer

/**
 * 导入结果契约。四类异常分别对应独立分支：网络异常、登录态失效、结构变化、字段缺失。
 */
sealed interface ImportResult {

    data class Success(
        val timetableId: Long,
        val courseCount: Int,
    ) : ImportResult

    data class NetworkFailure(
        val message: String,
        val cause: Throwable? = null,
    ) : ImportResult

    data class SessionExpired(
        val message: String,
        val cause: Throwable? = null,
    ) : ImportResult

    data class SchemaChanged(
        val message: String,
        val cause: Throwable? = null,
    ) : ImportResult

    data class MissingField(
        val field: String,
        val message: String,
    ) : ImportResult

    data class InvalidInput(
        val message: String,
        val cause: Throwable? = null,
    ) : ImportResult
}
