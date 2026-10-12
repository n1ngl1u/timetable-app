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
 * 液态玻璃主题：分层背景 + 半透明前景容器，零阴影，靠透明度与描边分层。
 *
 * 容器角色的透明度做层级差，且 surfaceContainerHigh 恰好比 surface 高 2 个百分点：
 * [0.04 Lowest] < [0.06 Dim = Variant] < [0.08 Low] < [0.10 surface = Container]
 * < [0.12 High] < [0.15 Highest] < [0.16 Bright]。
 * 只定义 surface 会让 NavigationBar（surfaceContainer）、ModalBottomSheet
 * （surfaceContainerLow）、TopAppBar（surface）失去玻璃效果。
 */
object LiquidGlassThemeTokens {

    private val LOWEST = 0.04f
    private val DIM = 0.06f
    private val VARIANT = 0.06f
    private val LOW = 0.08f
    private val SURFACE = 0.10f
    private val HIGH = 0.12f
    private val HIGHEST = 0.15f
    private val BRIGHT = 0.16f

    fun build(dark: Boolean): ThemeTokens {
        val ink = if (dark) Color(0xFFEAF0FF) else Color(0xFF0B1020)
        return ThemeTokens(
            id = ThemeId.LIQUID_GLASS,
            isDark = dark,
            period = DayPeriod.MORNING,
            colorScheme = schemeOf(dark, ink),
            typography = TimetableTypography,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
            ),
            elevation = ThemeElevation(card = 0.dp, sheet = 0.dp, appBar = 0.dp),
            motion = ThemeMotion(fastMillis = 200, mediumMillis = 380),
            background = if (dark) {
                BackgroundSpec.LiquidGlass(
                    base = listOf(Color(0xFF0B1020), Color(0xFF1B2540)),
                    blobA = Color(0xB34C7DFF),
                    blobB = Color(0x8C22D3EE),
                    highlight = Color(0x14FFFFFF),
                )
            } else {
                BackgroundSpec.LiquidGlass(
                    base = listOf(Color(0xFFEAF0FF), Color(0xFFD6E4FF)),
                    blobA = Color(0x8C7AA2FF),
                    blobB = Color(0x737FE3F5),
                    highlight = Color(0x59FFFFFF),
                )
            },
        )
    }

    private fun schemeOf(dark: Boolean, ink: Color): ColorScheme {
        val primary = if (dark) Color(0xFF7FD1FF) else Color(0xFF1D5FD0)
        val secondary = if (dark) Color(0xFFB39DFF) else Color(0xFF5B45C9)
        val tertiary = if (dark) Color(0xFF22D3EE) else Color(0xFF0E7C93)
        val error = if (dark) Color(0xFFFF8A9B) else Color(0xFFB3261E)
        val onPrimary = if (dark) Color(0xFF06121F) else Color.White
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = if (dark) 0.24f else 0.16f),
            onPrimaryContainer = if (dark) Color(0xFFDCF1FF) else Color(0xFF06213F),
            inversePrimary = secondary,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = secondary.copy(alpha = 0.22f),
            onSecondaryContainer = ink,
            tertiary = tertiary,
            onTertiary = onPrimary,
            tertiaryContainer = tertiary.copy(alpha = 0.22f),
            onTertiaryContainer = ink,
            background = Color.Transparent,
            onBackground = ink,
            surface = ink.copy(alpha = SURFACE),
            onSurface = ink,
            surfaceVariant = ink.copy(alpha = VARIANT),
            onSurfaceVariant = if (dark) Color(0xB3EAF0FF) else Color(0xB30B1020),
            surfaceTint = primary,
            inverseSurface = ink,
            inverseOnSurface = if (dark) Color(0xFF0B1020) else Color(0xFFEAF0FF),
            error = error,
            onError = onPrimary,
            errorContainer = error.copy(alpha = 0.22f),
            onErrorContainer = ink,
            outline = ink.copy(alpha = 0.18f),
            outlineVariant = ink.copy(alpha = 0.10f),
            scrim = Color(0xCC000000),
            surfaceDim = ink.copy(alpha = DIM),
            surfaceBright = ink.copy(alpha = BRIGHT),
            surfaceContainerLowest = ink.copy(alpha = LOWEST),
            surfaceContainerLow = ink.copy(alpha = LOW),
            surfaceContainer = ink.copy(alpha = SURFACE),
            surfaceContainerHigh = ink.copy(alpha = HIGH),
            surfaceContainerHighest = ink.copy(alpha = HIGHEST),
        )
    }
}
