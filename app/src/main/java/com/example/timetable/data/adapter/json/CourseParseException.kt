package com.example.timetable.data.adapter.json

/**
 * 课表结构错误。[field] 非空表示定位到具体字段缺失，导入链路据此区分
 * SchemaChanged 与 MissingField。
 */
class CourseParseException(
    message: String,
    val field: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
