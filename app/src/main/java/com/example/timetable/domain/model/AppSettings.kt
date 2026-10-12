package com.example.timetable.domain.model

/**
 * 非敏感设置项，落 DataStore。
 */
data class AppSettings(
    val themeId: String = THEME_SYSTEM,
    val pureModeEnabled: Boolean = false,
    val defaultReminderAdvanceMinutes: Int = ReminderRule.DEFAULT_ADVANCE_MINUTES,
    val activeSemesterId: Long = 0L,
    /**
     * null = 没有活跃课表（首次启动 / 课表已删空）。
     * 骨架期写入的哨兵值 0 无法与真实 id 区分，读取时归一为 null。
     */
    val activeTimetableId: Long? = null,
) {
    companion object {
        const val THEME_SYSTEM = "system"
    }
}
