package com.example.timetable.data.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 权限态查询与系统设置跳转。精确闹钟用 SCHEDULE_EXACT_ALARM（运行时授权），
 * 不使用 USE_EXACT_ALARM —— 那是闹钟类应用专用权限。
 */
@Singleton
class ReminderPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun canScheduleExactAlarms(): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false

    fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun exactAlarmSettingsIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))

    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    companion object {
        const val ACTION_REQUEST_EXACT_ALARM: String = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
    }
}
