package com.example.timetable.domain.model

/**
 * 课前提醒规则：提前量、生效周次、开关。
 * 生效周次为空表示跟随课程自身的 weeks；非空则与课程 weeks 取交集。
 */
data class ReminderRule(
    val id: Long = 0L,
    val courseId: Long,
    val advanceMinutes: Int = DEFAULT_ADVANCE_MINUTES,
    val enabled: Boolean = true,
    val weeks: List<Int> = emptyList(),
) {
    init {
        require(advanceMinutes >= 0) { "advanceMinutes 不能为负" }
    }

    companion object {
        const val DEFAULT_ADVANCE_MINUTES = 15

        /** UI 只暴露三档，避免用户选出无法解释的提前量。 */
        val ADVANCE_OPTIONS = listOf(15, 30, 60)

        /** 精确闹钟被拒后走 WorkManager，精度退化为 ±15min 级，不可保证具体时刻。 */
        const val FALLBACK_SYNC_INTERVAL_MINUTES = 15L
    }
}
