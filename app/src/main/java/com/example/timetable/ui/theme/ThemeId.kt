package com.example.timetable.ui.theme

/**
 * 主题标识。storageKey 是持久化契约，与枚举名解耦：
 * 用 storageKey 精确匹配而不是枚举名的 lowercase()，
 * 这样重命名枚举常量不会悄悄把用户已选的主题改写掉。
 */
enum class ThemeId(val storageKey: String) {
    DEFAULT("default"),
    TIME_OF_DAY("time_of_day"),
    LIQUID_GLASS("liquid_glass"),
    ;

    companion object {
        val FALLBACK: ThemeId = DEFAULT

        /** 大小写敏感；null、空串、旧值（如 "system"）、未知值全部回落 DEFAULT。 */
        fun fromStorage(raw: String?): ThemeId {
            val value = raw?.trim().orEmpty()
            return entries.firstOrNull { it.storageKey == value } ?: FALLBACK
        }
    }
}
