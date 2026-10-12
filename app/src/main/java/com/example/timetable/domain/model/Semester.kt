package com.example.timetable.domain.model

import java.time.LocalDate

/**
 * 学期 / 专业维度的课表分组标识。
 */
data class Semester(
    val id: Long = 0L,
    val name: String,
    val startDate: LocalDate,
    val major: String = "",
    val isActive: Boolean = false,
)
