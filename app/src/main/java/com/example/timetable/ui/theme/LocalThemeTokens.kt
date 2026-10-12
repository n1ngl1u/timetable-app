package com.example.timetable.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * token 注入点。用 static 变体：主题切换本来就重组整棵树，
 * 不需要细粒度失效追踪，static 的读取开销更低。
 */
val LocalThemeTokens = staticCompositionLocalOf<ThemeTokens> {
    error("LocalThemeTokens 未提供：内容必须包在 TimetableTheme 之内")
}

/** 语义糖：MaterialTheme.timetableTokens。 */
val MaterialTheme.timetableTokens: ThemeTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeTokens.current
