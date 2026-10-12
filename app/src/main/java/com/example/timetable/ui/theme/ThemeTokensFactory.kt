package com.example.timetable.ui.theme

import androidx.compose.material3.ColorScheme
import com.example.timetable.ui.theme.tokens.DefaultThemeTokens
import com.example.timetable.ui.theme.tokens.LiquidGlassThemeTokens
import com.example.timetable.ui.theme.tokens.TimeOfDayThemeTokens

/**
 * 主题 token 的唯一分派点。when 是穷尽的：
 * 新增 ThemeId 常量而忘了给 token，编译期就会失败。
 */
object ThemeTokensFactory {

    fun tokensFor(
        id: ThemeId,
        period: DayPeriod,
        dark: Boolean,
        dynamicScheme: ColorScheme?,
    ): ThemeTokens = when (id) {
        ThemeId.DEFAULT -> DefaultThemeTokens.build(period, dark, dynamicScheme)
        ThemeId.TIME_OF_DAY -> TimeOfDayThemeTokens.build(period)
        ThemeId.LIQUID_GLASS -> LiquidGlassThemeTokens.build(dark)
    }
}
