package com.example.timetable.domain.model

/**
 * 课前提醒规则：提前量为用户自定义，单位为分钟。
 */
data class ReminderRule(
    val id: Long = 0L,
    val courseId: Long,
    val advanceMinutes: Int = DEFAULT_ADVANCE_MINUTES,
    val enabled: Boolean = true,
) {
    init {
        require(advanceMinutes >= 0) { "advanceMinutes 不能为负" }
    }

    companion object {
        const val DEFAULT_ADVANCE_MINUTES = 15

        /** 精确闹钟被拒后 WorkManager 的最小调度周期，精度退化为 ±15 分钟。 */
        const val FALLBACK_SYNC_INTERVAL_MINUTES = 15L
    }
}
