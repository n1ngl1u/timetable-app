package com.example.timetable.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timetable.MainActivity
import com.example.timetable.R
import com.example.timetable.domain.reminder.ReminderNotificationContent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知渠道与投递。渠道创建幂等：createNotificationChannel 对同一 id 是更新语义，重复调用不抛。
 * POST_NOTIFICATIONS 被拒时只写日志，不弹通知，也不影响调度。
 */
@Singleton
class ReminderNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: ReminderPermissionChecker,
) {

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun notify(content: ReminderNotificationContent): Boolean {
        if (!permissionChecker.notificationsAllowed()) {
            Log.i(TAG, "通知被禁用，提醒仅记录: " + content.title + " / " + content.body)
            return false
        }
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchAppPendingIntent())
            .build()
        NotificationManagerCompat.from(context).notify(content.notificationId, notification)
        return true
    }

    fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun launchAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_LAUNCH,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "timetable_reminders"
        const val REQUEST_CODE_LAUNCH = 0x5EED
        private const val TAG = "TimetableReminder"
    }
}
