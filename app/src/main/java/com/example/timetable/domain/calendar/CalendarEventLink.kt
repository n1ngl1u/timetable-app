package com.example.timetable.domain.calendar

/**
 * 本地记录的「课程 -> 系统日历事件」映射。
 * 方案 A′ 下一门课可能对应多条事件（每个连续段一条），因此以
 * (courseId, segmentStartWeek) 作为业务唯一键。
 */
data class CalendarEventLink(
    val id: Long = 0L,
    val courseId: Long,
    val calendarEventId: Long,
    val segmentStartWeek: Int,
    val segmentEndWeek: Int,
    val contentHash: String,
    val syncedAt: Long,
)
