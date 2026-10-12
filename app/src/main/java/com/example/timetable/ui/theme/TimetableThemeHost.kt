package com.example.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 把「持久化偏好 + 系统深浅色 + 当前时段 + 动态取色」合成一份 ThemeTokens。
 * 分辨率最低的那一层，其余组件一律只认 ThemeTokens。
 */
@Composable
fun TimetableThemeHost(
    viewModel: ThemeViewModel,
    content: @Composable () -> Unit,
) {
    val request by viewModel.request.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleStartEffect(viewModel) {
        viewModel.setTickerActive(true)
        onStopOrDispose { viewModel.setTickerActive(false) }
    }

    val systemDark = isSystemInDarkTheme()
    val dark = when (request.darkModePreference) {
        DarkModePreference.FOLLOW_SYSTEM -> systemDark
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }

    val tokens = remember(request.themeId, dark, request.period) {
        // minSdk 31 是动态取色的最低版本，运行时恒可用，不需要版本分支
        val dynamicScheme = if (dark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        ThemeTokensFactory.tokensFor(
            id = request.themeId,
            period = request.period,
            dark = dark,
            dynamicScheme = dynamicScheme,
        )
    }

    TimetableTheme(tokens = tokens, content = content)
}
