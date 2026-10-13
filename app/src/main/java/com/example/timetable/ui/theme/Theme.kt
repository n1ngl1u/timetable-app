package com.example.timetable.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * 主题宿主。背景层画在内容之下；ColorScheme 由 token 提供，
 * 因此既有页面里的 MaterialTheme.colorScheme 引用一律无需改动。
 *
 * 主题切换**不做过渡动画**，瞬时完成。原因：
 * - Material You 与系统级深色模式切换本来就是瞬时的，手动切换应当与系统行为一致；
 * - 曾经只对 background / surface / primary 三个角色做 200ms 过渡，其余几十个角色
 *   （onBackground / onSurface / surfaceVariant / outline / container 系列）瞬时跳变，
 *   两套节奏混在一起产生明显的视觉撕裂 —— 这正是要消除的问题；
 * - 那 200ms 还要求整棵子树每帧重组约 12 次，取消后每帧重组开销为零。
 */
@Composable
fun TimetableTheme(
    tokens: ThemeTokens,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalThemeTokens provides tokens) {
        MaterialTheme(
            colorScheme = tokens.colorScheme,
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
