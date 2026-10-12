package com.example.timetable.domain.reminder

import com.example.timetable.domain.model.ReminderSchedule

/** 调度决策结果：走精确闹钟、走 WorkManager 降级、或跳过。 */
sealed interface ReminderDispatch {

    val ruleId: Long

    data class Exact(
        override val ruleId: Long,
        val triggerAtMillis: Long,
    ) : ReminderDispatch

    data class Inexact(
        override val ruleId: Long,
        val triggerAtMillis: Long,
    ) : ReminderDispatch

    data class Skipped(
        override val ruleId: Long,
        val reason: SkipReason,
    ) : ReminderDispatch
}

enum class SkipReason {
    DISABLED,
    NO_TRIGGER_WINDOW,
}

/**
 * 纯决策函数：权限态 + 规则 -> 走哪条路径。无 Android 依赖，可 JVM 单测。
 */
object ReminderSchedulingPolicy {

    fun decide(
        schedule: ReminderSchedule,
        nextTriggerAtMillis: Long?,
        canScheduleExactAlarms: Boolean,
    ): ReminderDispatch {
        if (!schedule.enabled) {
            return ReminderDispatch.Skipped(schedule.ruleId, SkipReason.DISABLED)
        }
        if (nextTriggerAtMillis == null) {
            return ReminderDispatch.Skipped(schedule.ruleId, SkipReason.NO_TRIGGER_WINDOW)
        }
        return if (canScheduleExactAlarms) {
            ReminderDispatch.Exact(schedule.ruleId, nextTriggerAtMillis)
        } else {
            ReminderDispatch.Inexact(schedule.ruleId, nextTriggerAtMillis)
        }
    }
}
