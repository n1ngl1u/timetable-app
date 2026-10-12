package com.example.timetable.domain.reminder

import com.example.timetable.domain.model.ReminderSchedule
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 纯触发时间计算：无时钟、无 Android 依赖，now 与 zone 由调用方注入以便单测。
 *
 * 规则：由课表 startDate（第 1 周周一）推导每个生效周的该 weekday 日期，
 * 取节次开始时间减去提前量，返回第一个晚于 now 的时刻；全部已过则返回 null。
 */
object ReminderTriggerCalculator {

    fun nextTriggerAtMillis(
        schedule: ReminderSchedule,
        nowMillis: Long,
        zone: ZoneId,
    ): Long? {
        if (schedule.advanceMinutes < 0) return null
        val startTime = parseTime(schedule.startTime) ?: return null
        val weeks = schedule.effectiveWeeks.filter { it > 0 }.distinct().sorted()
        if (weeks.isEmpty()) return null
        for (week in weeks) {
            val date = schedule.timetableStartDate
                .plusWeeks((week - 1).toLong())
                .plusDays((schedule.weekday - 1).toLong())
            val candidate = date
                .atTime(startTime)
                .minusMinutes(schedule.advanceMinutes.toLong())
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            if (candidate > nowMillis) return candidate
        }
        return null
    }

    private fun parseTime(raw: String?): LocalTime? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return try {
            LocalTime.parse(value)
        } catch (error: DateTimeParseException) {
            null
        }
    }

    fun nowAt(nowMillis: Long, zone: ZoneId): java.time.LocalDateTime =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
}
