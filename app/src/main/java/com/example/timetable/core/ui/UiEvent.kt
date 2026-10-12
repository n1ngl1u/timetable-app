package com.example.timetable.core.ui

/**
 * 一次性副作用事件；跳转系统设置用于精确闹钟授权引导（拒绝后降级 WorkManager）。
 */
sealed interface UiEvent {

    data class ShowMessage(val text: String) : UiEvent

    data class Navigate(val route: String) : UiEvent

    data class OpenSystemSettings(val action: String) : UiEvent

    /** 仅在用户主动开启提醒时请求通知权限，冷启动不弹。 */
    data object RequestNotificationPermission : UiEvent

    /** 仅在设置页打开「日历同步」时请求 READ/WRITE_CALENDAR。 */
    data object RequestCalendarPermission : UiEvent
}
