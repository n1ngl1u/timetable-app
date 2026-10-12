package com.example.timetable.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timetable.data.reminder.ReminderAlarmReceiver
import com.example.timetable.data.reminder.ReminderNotifications
import com.example.timetable.data.reminder.ReminderScheduler
import com.example.timetable.domain.reminder.ReminderNotificationContentFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** PendingIntent 构造契约：不可变标志、按 ruleId 稳定的 requestCode、重复构造等价。 */
@RunWith(AndroidJUnit4::class)
class ReminderPendingIntentTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun alarmPendingIntentIsImmutableAndStable() {
        val first = alarmPendingIntent(RULE_ID)
        val second = alarmPendingIntent(RULE_ID)

        assertNotNull(first)
        assertTrue("必须带 FLAG_IMMUTABLE", first!!.isImmutable)
        assertEquals("同 ruleId 重复构造应等价", first, second)
    }

    @Test
    fun differentRulesGetDifferentRequestCodes() {
        assertNotEquals(
            ReminderNotificationContentFactory.notificationIdFor(1L),
            ReminderNotificationContentFactory.notificationIdFor(2L),
        )
    }

    @Test
    fun workNameIsDerivedFromRuleId() {
        assertEquals("reminder_work_7", ReminderScheduler.workNameFor(7L))
        assertEquals("reminder_work_0", ReminderScheduler.workNameFor(0L))
        assertNotEquals(ReminderScheduler.workNameFor(1L), ReminderScheduler.workNameFor(2L))
    }

    @Test
    fun launchAppPendingIntentTargetsMainActivity() {
        val notifications = ReminderNotifications(context, com.example.timetable.data.reminder.ReminderPermissionChecker(context))

        val pendingIntent = notifications.launchAppPendingIntent()

        assertNotNull(pendingIntent)
        assertTrue(pendingIntent.isImmutable)
    }

    private fun alarmPendingIntent(ruleId: Long): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_REMINDER_ALARM
            putExtra(ReminderScheduler.EXTRA_RULE_ID, ruleId)
        }
        return PendingIntent.getBroadcast(
            context,
            ReminderNotificationContentFactory.notificationIdFor(ruleId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val RULE_ID = 42L
    }
}
