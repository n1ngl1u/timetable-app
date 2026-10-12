package com.example.timetable.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** 阴影档位。液态玻璃全部为 0，靠半透明与描边分层。 */
@Immutable
data class ThemeElevation(
    val card: Dp,
    val sheet: Dp,
    val appBar: Dp,
)

/** 动效参数。主题切换过渡不使用这里的值，见 Theme.kt 的 THEME_TRANSITION_MILLIS。 */
@Immutable
data class ThemeMotion(
    val fastMillis: Int,
    val mediumMillis: Int,
)

/** 背景绘制规格。数据驱动，ThemeBackground 内部不按主题分支。 */
sealed interface BackgroundSpec {

    /** 默认主题：不绘制，由 ColorScheme.background 承担。 */
    data object None : BackgroundSpec

    /** 时间动态：每个时段一组渐变 stops，按 start 到 end 垂直铺满。 */
    @Immutable
    data class TimeOfDay(val stops: Map<DayPeriod, List<Color>>) : BackgroundSpec

    /**
     * 液态玻璃：base 垂直渐变 + 两个光斑 + 顶部高光。
     * 光斑是唯一使用 RenderEffect（Modifier.blur）的层，且完全静态。
     */
    @Immutable
    data class LiquidGlass(
        val base: List<Color>,
        val blobA: Color,
        val blobB: Color,
        val highlight: Color,
    ) : BackgroundSpec
}

/**
 * 一套主题的完整 token。颜色直接携带 Material3 的 ColorScheme，
 * 因此既有的 MaterialTheme.colorScheme 引用无需改动即可跟随主题。
 */
@Immutable
data class ThemeTokens(
    val id: ThemeId,
    val isDark: Boolean,
    /** 当前时段；时间动态主题据此取渐变，其余主题不影响绘制。 */
    val period: DayPeriod,
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val elevation: ThemeElevation,
    val motion: ThemeMotion,
    val background: BackgroundSpec,
)
