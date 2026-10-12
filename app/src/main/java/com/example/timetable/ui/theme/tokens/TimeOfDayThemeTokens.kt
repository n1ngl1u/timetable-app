package com.example.timetable.ui.theme.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timetable.ui.theme.BackgroundSpec
import com.example.timetable.ui.theme.DayPeriod
import com.example.timetable.ui.theme.ThemeElevation
import com.example.timetable.ui.theme.ThemeId
import com.example.timetable.ui.theme.ThemeMotion
import com.example.timetable.ui.theme.ThemeTokens
import com.example.timetable.ui.theme.TimetableTypography

/**
 * 时间动态主题：背景随四个时段切换渐变。
 *
 * **夜间时段强制深色 ColorScheme**：20:00 到 05:59 的渐变是深色底，
 * 浅色 UI 压在上面不可读。这是本主题独有的行为，另外两套主题一律听从深浅色偏好。
 *
 * 各容器的 surface 取半透明（白或夜色覆盖在渐变之上），
 * 否则日历单元格、TopAppBar 这类以 surface 为底色的组件会在渐变上形成补丁。
 */
object TimeOfDayThemeTokens {

    private val MORNING_STOPS = listOf(Color(0xFFFFF4E6), Color(0xFFFFD9A0))
    private val AFTERNOON_STOPS = listOf(Color(0xFFE8F4FF), Color(0xFFB9DCFF))
    private val DUSK_STOPS = listOf(Color(0xFFFFE9EF), Color(0xFFF3B7C8))
    private val NIGHT_STOPS = listOf(Color(0xFF101627), Color(0xFF1D2540))

    fun build(period: DayPeriod): ThemeTokens {
        val dark = period == DayPeriod.NIGHT
        return ThemeTokens(
            id = ThemeId.TIME_OF_DAY,
            isDark = dark,
            period = period,
            colorScheme = schemeOf(period, dark),
            typography = TimetableTypography,
            shapes = Shapes(
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
            ),
            elevation = ThemeElevation(card = 0.dp, sheet = 8.dp, appBar = 0.dp),
            motion = ThemeMotion(fastMillis = 150, mediumMillis = 300),
            background = BackgroundSpec.TimeOfDay(
                stops = mapOf(
                    DayPeriod.MORNING to MORNING_STOPS,
                    DayPeriod.AFTERNOON to AFTERNOON_STOPS,
                    DayPeriod.DUSK to DUSK_STOPS,
                    DayPeriod.NIGHT to NIGHT_STOPS,
                ),
            ),
        )
    }

    private fun schemeOf(period: DayPeriod, dark: Boolean): ColorScheme {
        val primary = when (period) {
            DayPeriod.MORNING -> Color(0xFFD97706)
            DayPeriod.AFTERNOON -> Color(0xFF1D6FD0)
            DayPeriod.DUSK -> Color(0xFFB33A63)
            DayPeriod.NIGHT -> Color(0xFF7FB2FF)
        }
        val ink = when (period) {
            DayPeriod.MORNING -> Color(0xFF4A2C00)
            DayPeriod.AFTERNOON -> Color(0xFF0B2A4A)
            DayPeriod.DUSK -> Color(0xFF4A1224)
            DayPeriod.NIGHT -> Color(0xFFE6ECF7)
        }
        val overlay = ink
        return if (dark) {
            darkColorScheme(
                primary = primary,
                onPrimary = Color(0xFF0A1020),
                primaryContainer = primary.copy(alpha = 0.24f),
                onPrimaryContainer = ink,
                background = Color.Transparent,
                onBackground = ink,
                surface = overlay.copy(alpha = SURFACE),
                onSurface = ink,
                surfaceVariant = overlay.copy(alpha = VARIANT),
                onSurfaceVariant = ink.copy(alpha = 0.78f),
                surfaceTint = primary,
                outline = ink.copy(alpha = 0.20f),
                outlineVariant = ink.copy(alpha = 0.12f),
                surfaceDim = overlay.copy(alpha = DIM),
                surfaceBright = overlay.copy(alpha = BRIGHT),
                surfaceContainerLowest = overlay.copy(alpha = LOWEST),
                surfaceContainerLow = overlay.copy(alpha = LOW),
                surfaceContainer = overlay.copy(alpha = SURFACE),
                surfaceContainerHigh = overlay.copy(alpha = HIGH),
                surfaceContainerHighest = overlay.copy(alpha = HIGHEST),
                inverseSurface = ink,
                inverseOnSurface = Color(0xFF101627),
                scrim = Color(0xCC000000),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = Color.White,
                primaryContainer = primary.copy(alpha = 0.18f),
                onPrimaryContainer = ink,
                background = Color.Transparent,
                onBackground = ink,
                surface = Color.White.copy(alpha = SURFACE),
                onSurface = ink,
                surfaceVariant = Color.White.copy(alpha = VARIANT),
                onSurfaceVariant = ink.copy(alpha = 0.78f),
                surfaceTint = primary,
                outline = ink.copy(alpha = 0.20f),
                outlineVariant = ink.copy(alpha = 0.12f),
                surfaceDim = Color.White.copy(alpha = DIM),
                surfaceBright = Color.White.copy(alpha = BRIGHT),
                surfaceContainerLowest = Color.White.copy(alpha = LOWEST),
                surfaceContainerLow = Color.White.copy(alpha = LOW),
                surfaceContainer = Color.White.copy(alpha = SURFACE),
                surfaceContainerHigh = Color.White.copy(alpha = HIGH),
                surfaceContainerHighest = Color.White.copy(alpha = HIGHEST),
                inverseSurface = ink,
                inverseOnSurface = Color.White,
                scrim = Color(0x99000000),
                error = Color(0xFFB3261E),
                onError = Color.White,
            )
        }
    }

    private const val LOWEST = 0.45f
    private const val DIM = 0.50f
    private const val VARIANT = 0.50f
    private const val LOW = 0.55f
    private const val SURFACE = 0.72f
    private const val HIGH = 0.80f
    private const val HIGHEST = 0.88f
    private const val BRIGHT = 0.95f
}
