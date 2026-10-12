package com.example.timetable.domain.model

/**
 * 非敏感设置项，落 DataStore。
 */
data class AppSettings(
    val themeId: String = THEME_SYSTEM,
    val pureModeEnabled: Boolean = false,
    val defaultReminderAdvanceMinutes: Int = ReminderRule.DEFAULT_ADVANCE_MINUTES,
    val activeSemesterId: Long = 0L,
    val activeTimetableId: Long = 0L,
) {
    companion object {
        const val THEME_SYSTEM = "system"
    }
}
