package com.example.timetable.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 主题宿主。背景层画在内容之下；ColorScheme 由 token 提供，
 * 因此既有页面里的 MaterialTheme.colorScheme 引用一律无需改动。
 *
 * 主题切换过渡：只对 background / surface / primary 三个角色做 200ms 变色，
 * 其余角色瞬时切换。过渡时长是固定常量而不是 tokens.motion，
 * 否则过渡速度会取决于「切到哪个主题」，产生不一致的手感。
 * 代价：过渡的 200ms 内 MaterialTheme 每帧换色，整个子树会重组约 12 次；
 * 只在切换主题时发生，不做常驻动画。
 */
@Composable
fun TimetableTheme(
    tokens: ThemeTokens,
    content: @Composable () -> Unit,
) {
    val target = tokens.colorScheme
    val background by animateColorAsState(
        targetValue = target.background,
        animationSpec = tween(THEME_TRANSITION_MILLIS),
        label = "themeBackground",
    )
    val surface by animateColorAsState(
        targetValue = target.surface,
        animationSpec = tween(THEME_TRANSITION_MILLIS),
        label = "themeSurface",
    )
    val primary by animateColorAsState(
        targetValue = target.primary,
        animationSpec = tween(THEME_TRANSITION_MILLIS),
        label = "themePrimary",
    )
    val animatedScheme = remember(target, background, surface, primary) {
        target.copy(background = background, surface = surface, primary = primary)
    }
    val animatedTokens = remember(tokens, animatedScheme) {
        tokens.copy(colorScheme = animatedScheme)
    }

    CompositionLocalProvider(LocalThemeTokens provides animatedTokens) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = tokens.typography,
            shapes = tokens.shapes,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ThemeBackground(
                    spec = tokens.background,
                    period = tokens.period,
                    modifier = Modifier.matchParentSize(),
                )
                content()
            }
        }
    }
}

/** 主题切换过渡时长。刻意不使用 ThemeMotion，见类 KDoc。 */
const val THEME_TRANSITION_MILLIS = 200
