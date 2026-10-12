package com.example.timetable.domain.model

import java.time.LocalDate

/**
 * 课表聚合根，对应导入 JSON 的顶层结构。
 */
data class Timetable(
    val id: Long = 0L,
    val tableName: String,
    val startDate: LocalDate,
    val semesterId: Long = 0L,
    val timeSlots: List<TimeSlot> = emptyList(),
    val courses: List<Course> = emptyList(),
) {
    fun sectionsOf(weekday: Int, week: Int): List<Course> =
        courses.filter { it.weekday == weekday && it.isActiveInWeek(week) }
            .sortedBy { it.startSection }
}
