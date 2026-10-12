package com.example.timetable.ui.theme

/** 深浅色偏好三态。存字符串，非法值回落 FOLLOW_SYSTEM。 */
enum class DarkModePreference(val storageKey: String) {
    FOLLOW_SYSTEM("follow_system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        val FALLBACK: DarkModePreference = FOLLOW_SYSTEM

        fun fromStorage(raw: String?): DarkModePreference {
            val value = raw?.trim().orEmpty()
            return entries.firstOrNull { it.storageKey == value } ?: FALLBACK
        }
    }
}
