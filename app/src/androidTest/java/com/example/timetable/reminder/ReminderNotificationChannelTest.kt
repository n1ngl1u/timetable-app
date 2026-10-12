package com.example.timetable.reminder

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timetable.data.reminder.ReminderNotifications
import com.example.timetable.data.reminder.ReminderPermissionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 通知渠道创建幂等：连续创建两次不抛，且渠道属性正确。 */
@RunWith(AndroidJUnit4::class)
class ReminderNotificationChannelTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun notifications() = ReminderNotifications(context, ReminderPermissionChecker(context))

    @Test
    fun channelCreationIsIdempotent() {
        val notifications = notifications()

        notifications.ensureChannel()
        notifications.ensureChannel()
        notifications.ensureChannel()

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ReminderNotifications.CHANNEL_ID)
        assertNotNull("渠道应已创建", channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue("渠道描述应来自资源", channel.description.isNotEmpty())
        assertTrue("渠道 id 必须是常量本身", channel.id == ReminderNotifications.CHANNEL_ID)
    }

    @Test
    fun permissionStateIsReadable() {
        val checker = ReminderPermissionChecker(context)

        // 不断言具体取值（取决于设备授权状态），只断言可读且不抛
        checker.canScheduleExactAlarms()
        checker.notificationsAllowed()

        assertNotNull(checker.exactAlarmSettingsIntent())
        assertNotNull(checker.notificationSettingsIntent())
    }
}
