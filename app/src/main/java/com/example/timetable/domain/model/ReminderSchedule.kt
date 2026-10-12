package com.example.timetable.domain.model

import java.time.LocalDate

/**
 * 一条提醒规则连同它所在课程、课表、节次时间的扁平投影，是调度计算的唯一输入。
 */
data class ReminderSchedule(
    val ruleId: Long,
    val courseId: Long,
    val courseName: String,
    val location: String,
    val weekday: Int,
    val startSection: Int,
    val startTime: String?,
    val timetableId: Long,
    val timetableName: String,
    val timetableStartDate: LocalDate,
    val advanceMinutes: Int,
    val enabled: Boolean,
    val ruleWeeks: List<Int>,
    val courseWeeks: List<Int>,
) {
    /**
     * 规则未指定周次时跟随课程周次；指定了则取交集。
     * courseWeeks 为空表示该课程本身无排课（模块 1 的显式空数组语义），交集必为空，
     * 不能当作「无约束」处理。
     */
    val effectiveWeeks: List<Int>
        get() = if (ruleWeeks.isEmpty()) {
            courseWeeks
        } else {
            ruleWeeks.filter { week -> courseWeeks.contains(week) }
        }
}
