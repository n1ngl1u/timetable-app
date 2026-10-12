package com.example.timetable.domain.reminder

import com.example.timetable.domain.model.ReminderSchedule

/** 通知文案与其稳定 id，纯数据，由 Android 侧负责真正投递。 */
data class ReminderNotificationContent(
    val notificationId: Int,
    val title: String,
    val body: String,
)

/**
 * 纯文案组装：标题取课程名，正文由「教室 · 开始时间」拼接，缺失项自动省略。
 * notificationId 用折叠异或把 Long 压到 Int，避免直接 toInt() 的高位截断。
 */
object ReminderNotificationContentFactory {

    private const val SEPARATOR = " · "

    fun create(schedule: ReminderSchedule): ReminderNotificationContent {
        val body = listOf(schedule.location, schedule.startTime.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = SEPARATOR)
        return ReminderNotificationContent(
            notificationId = notificationIdFor(schedule.ruleId),
            title = schedule.courseName,
            body = body,
        )
    }

    fun notificationIdFor(ruleId: Long): Int = (ruleId xor (ruleId ushr 32)).toInt()
}
