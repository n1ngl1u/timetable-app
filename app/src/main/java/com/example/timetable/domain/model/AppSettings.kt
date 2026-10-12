package com.example.timetable.domain.model

/**
 * 非敏感设置项，落 DataStore。
 */
data class AppSettings(
    val themeId: String = DEFAULT_THEME_ID,
    /** DarkModePreference.storageKey；非法值在 UI 层回落 FOLLOW_SYSTEM。 */
    val darkModePreference: String = DEFAULT_DARK_MODE_PREFERENCE,
    val pureModeEnabled: Boolean = false,
    val remindersEnabled: Boolean = false,
    val calendarSyncEnabled: Boolean = false,
    val defaultReminderAdvanceMinutes: Int = ReminderRule.DEFAULT_ADVANCE_MINUTES,
    val activeSemesterId: Long = 0L,
    /**
     * null = 没有活跃课表（首次启动 / 课表已删空）。
     * 骨架期写入的哨兵值 0 无法与真实 id 区分，读取时归一为 null。
     */
    val activeTimetableId: Long? = null,
) {
    companion object {
        /** 与 ui.theme.ThemeId.DEFAULT.storageKey 一致；domain 不依赖 UI 层，因此这里重复字面量。 */
        const val DEFAULT_THEME_ID = "default"

        /** 与 ui.theme.DarkModePreference.FOLLOW_SYSTEM.storageKey 一致。 */
        const val DEFAULT_DARK_MODE_PREFERENCE = "follow_system"

        /** 骨架期写入过的旧值，读取侧由 ThemeId.fromStorage 归一为 DEFAULT。 */
        const val LEGACY_THEME_SYSTEM = "system"
    }
}
