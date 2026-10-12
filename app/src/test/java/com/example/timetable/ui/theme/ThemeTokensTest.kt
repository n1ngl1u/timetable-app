package com.example.timetable.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    /** 函数名刻意不使用 tokens：避免被局部变量遮蔽。 */
    private fun tokensOf(
        id: ThemeId,
        dark: Boolean = false,
        period: DayPeriod = DayPeriod.MORNING,
    ) = ThemeTokensFactory.tokensFor(id, period, dark, dynamicScheme = null)

    @Test
    fun everyThemeIsCompleteInBothDarkModes() {
        ThemeId.entries.forEach { id ->
            listOf(false, true).forEach { dark ->
                val tokens = tokensOf(id, dark)
                val label = id.storageKey + "/dark=" + dark

                assertEquals(label, id, tokens.id)
                // 时间动态主题的深浅色由时段决定，刻意不看 dark 参数
                val expectedDark = when (id) {
                    ThemeId.TIME_OF_DAY -> tokens.period == DayPeriod.NIGHT
                    else -> dark
                }
                assertEquals(label + " 的 isDark 判定错误", expectedDark, tokens.isDark)

                val scheme = tokens.colorScheme
                val roles = listOf(
                    "primary" to scheme.primary,
                    "onPrimary" to scheme.onPrimary,
                    "primaryContainer" to scheme.primaryContainer,
                    "onPrimaryContainer" to scheme.onPrimaryContainer,
                    "secondary" to scheme.secondary,
                    "secondaryContainer" to scheme.secondaryContainer,
                    "tertiary" to scheme.tertiary,
                    "background" to scheme.background,
                    "onBackground" to scheme.onBackground,
                    "surface" to scheme.surface,
                    "onSurface" to scheme.onSurface,
                    "surfaceVariant" to scheme.surfaceVariant,
                    "onSurfaceVariant" to scheme.onSurfaceVariant,
                    "surfaceTint" to scheme.surfaceTint,
                    "surfaceDim" to scheme.surfaceDim,
                    "surfaceBright" to scheme.surfaceBright,
                    "surfaceContainerLowest" to scheme.surfaceContainerLowest,
                    "surfaceContainerLow" to scheme.surfaceContainerLow,
                    "surfaceContainer" to scheme.surfaceContainer,
                    "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                    "surfaceContainerHighest" to scheme.surfaceContainerHighest,
                    "inverseSurface" to scheme.inverseSurface,
                    "inverseOnSurface" to scheme.inverseOnSurface,
                    "outline" to scheme.outline,
                    "outlineVariant" to scheme.outlineVariant,
                    "scrim" to scheme.scrim,
                    "error" to scheme.error,
                    "onError" to scheme.onError,
                )
                roles.forEach { (name, color) ->
                    assertNotEquals(label + " 的 " + name + " 未赋值", Color.Unspecified, color)
                }

                assertTrue(label + " 缺少 bodyLarge", tokens.typography.bodyLarge.fontSize.value > 0f)
                assertTrue(label + " 缺少 titleLarge", tokens.typography.titleLarge.fontSize.value > 0f)
                assertTrue(label + " 缺少 labelMedium", tokens.typography.labelMedium.fontSize.value > 0f)

                // CornerSize 不暴露数值，改用与显式形状相等性判断
                assertNotEquals(label + " small 圆角为 0", RoundedCornerShape(0.dp), tokens.shapes.small)
                assertNotEquals(label + " medium 圆角为 0", RoundedCornerShape(0.dp), tokens.shapes.medium)
                assertNotEquals(label + " large 圆角为 0", RoundedCornerShape(0.dp), tokens.shapes.large)

                assertTrue(label + " fastMillis 非正", tokens.motion.fastMillis > 0)
                assertTrue(label + " mediumMillis 非正", tokens.motion.mediumMillis > 0)
                assertTrue(label + " card 阴影为负", tokens.elevation.card.value >= 0f)
                assertTrue(label + " sheet 阴影为负", tokens.elevation.sheet.value >= 0f)
            }
        }
    }

    @Test
    fun timeOfDayNightForcesDarkAndMorningForcesLight() {
        assertTrue(
            "夜间必须强制深色，无视深浅色偏好",
            tokensOf(ThemeId.TIME_OF_DAY, dark = false, period = DayPeriod.NIGHT).isDark,
        )
        assertTrue(
            "晨间必须强制浅色，无视深浅色偏好",
            !tokensOf(ThemeId.TIME_OF_DAY, dark = true, period = DayPeriod.MORNING).isDark,
        )
    }

    @Test
    fun timeOfDayProvidesStopsForAllFourPeriods() {
        val spec = tokensOf(ThemeId.TIME_OF_DAY).background
        assertTrue(spec is BackgroundSpec.TimeOfDay)
        val stops = (spec as BackgroundSpec.TimeOfDay).stops
        assertEquals(DayPeriod.entries.toSet(), stops.keys)
        stops.values.forEach { colors -> assertTrue("渐变至少两个色停", colors.size >= 2) }
    }

    @Test
    fun defaultThemeDrawsNoBackgroundLayer() {
        assertEquals(BackgroundSpec.None, tokensOf(ThemeId.DEFAULT).background)
    }

    @Test
    fun liquidGlassCarriesBackgroundAndZeroElevation() {
        val glass = tokensOf(ThemeId.LIQUID_GLASS)

        assertTrue(glass.background is BackgroundSpec.LiquidGlass)
        assertEquals("玻璃靠透明度分层，不靠阴影", 0f, glass.elevation.sheet.value, 0f)
        assertEquals(
            "默认主题 medium 圆角应为 12dp",
            RoundedCornerShape(12.dp),
            tokensOf(ThemeId.DEFAULT).shapes.medium,
        )
        assertEquals("玻璃 medium 圆角应为 20dp", RoundedCornerShape(20.dp), glass.shapes.medium)
    }

    @Test
    fun liquidGlassSurfaceContainersAreTieredWithTwoPointGap() {
        listOf(false, true).forEach { dark ->
            val scheme = tokensOf(ThemeId.LIQUID_GLASS, dark).colorScheme
            assertTrue(
                "Lowest 必须最透",
                scheme.surfaceContainerLowest.alpha < scheme.surfaceContainerLow.alpha,
            )
            assertTrue(
                "Low 必须比 surface 更透",
                scheme.surfaceContainerLow.alpha < scheme.surface.alpha,
            )
            // Color 的 alpha 是 8 位，0.10/0.12 会被量化成 26/255 与 31/255，
            // 因此 2 个百分点的层级差要按 1/255 的量化步长来判定
            assertEquals(
                "surfaceContainerHigh 必须比 surface 高 2 个百分点",
                scheme.surface.alpha + 0.02f,
                scheme.surfaceContainerHigh.alpha,
                1f / 255f,
            )
            assertTrue(
                "Highest 必须比 High 更实",
                scheme.surfaceContainerHigh.alpha < scheme.surfaceContainerHighest.alpha,
            )
            assertTrue(
                "Bright 必须最实",
                scheme.surfaceContainerHighest.alpha < scheme.surfaceBright.alpha,
            )
            assertTrue("玻璃底必须透出背景", scheme.background.alpha < 0.5f)
        }
    }

    @Test
    fun theThreeThemesAreVisiblyDifferent() {
        val default = tokensOf(ThemeId.DEFAULT)
        val timeOfDay = tokensOf(ThemeId.TIME_OF_DAY)
        val glass = tokensOf(ThemeId.LIQUID_GLASS)

        assertNotEquals(default.colorScheme.surface, glass.colorScheme.surface)
        assertNotEquals(default.shapes.medium, glass.shapes.medium)
        assertNotEquals(default.motion.mediumMillis, glass.motion.mediumMillis)
        assertNotEquals(timeOfDay.background, glass.background)
        assertNotEquals(default.background, timeOfDay.background)
    }
}
