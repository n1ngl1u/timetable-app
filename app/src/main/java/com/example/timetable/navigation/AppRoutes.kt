package com.example.timetable.navigation

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.timetable.R
import kotlinx.serialization.Serializable

/** 类型安全路由：navigation-compose 会为每个 @Serializable 目的地生成稳定路由。 */
@Serializable
data object TimetableRoute

@Serializable
data object CalendarRoute

@Serializable
data object SettingsRoute

/**
 * 导航目的地元数据。route 用 Any 是为了直接喂给 navigate(route: Any)，无需字符串拼接。
 * 三个图标都用本地矢量：Material3 1.4.0 已不再传递 material-icons-core，
 * 而 material-icons-extended 体积过大，不值得为一个图标引入。
 */
enum class AppDestination(
    @StringRes val labelRes: Int,
    val iconRes: Int,
) {
    TIMETABLE(R.string.tab_timetable, R.drawable.ic_timetable) {
        override val route: Any get() = TimetableRoute
    },
    CALENDAR(R.string.tab_calendar, R.drawable.ic_calendar_month) {
        override val route: Any get() = CalendarRoute
    },
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings) {
        override val route: Any get() = SettingsRoute
    };

    abstract val route: Any

    @Composable
    fun icon() {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = stringResource(labelRes),
        )
    }
}
