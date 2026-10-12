package com.example.timetable.ui.theme.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp
import com.example.timetable.ui.theme.BackgroundSpec
import com.example.timetable.ui.theme.DayPeriod
import com.example.timetable.ui.theme.ThemeElevation
import com.example.timetable.ui.theme.ThemeId
import com.example.timetable.ui.theme.ThemeMotion
import com.example.timetable.ui.theme.ThemeTokens
import com.example.timetable.ui.theme.TimetableBlue
import com.example.timetable.ui.theme.TimetableBlueDark
import com.example.timetable.ui.theme.TimetableErrorDark
import com.example.timetable.ui.theme.TimetableErrorLight
import com.example.timetable.ui.theme.TimetableSurfaceDark
import com.example.timetable.ui.theme.TimetableSurfaceLight
import com.example.timetable.ui.theme.TimetableTypography

/**
 * 默认主题：Material You 动态取色，静态调色板仅作回落。
 *
 * minSdk 31 恰好是动态取色的最低版本，因此**运行时恒走动态取色**，
 * 静态回落分支当前只由 JVM 单测覆盖（传入 dynamicScheme = null）。
 * 若未来下调 minSdk，回落分支会自动成为低版本设备的真实路径，无需再改代码。
 */
object DefaultThemeTokens {

    fun build(period: DayPeriod, dark: Boolean, dynamicScheme: ColorScheme?): ThemeTokens =
        ThemeTokens(
            id = ThemeId.DEFAULT,
            isDark = dark,
            period = period,
            colorScheme = dynamicScheme ?: staticFallback(dark),
            typography = TimetableTypography,
            shapes = Shapes(
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
            ),
            elevation = ThemeElevation(card = 0.dp, sheet = 8.dp, appBar = 0.dp),
            motion = ThemeMotion(fastMillis = 150, mediumMillis = 300),
            background = BackgroundSpec.None,
        )

    fun staticFallback(dark: Boolean): ColorScheme = if (dark) {
        darkColorScheme(
            primary = TimetableBlueDark,
            surface = TimetableSurfaceDark,
            error = TimetableErrorDark,
        )
    } else {
        lightColorScheme(
            primary = TimetableBlue,
            surface = TimetableSurfaceLight,
            error = TimetableErrorLight,
        )
    }
}
