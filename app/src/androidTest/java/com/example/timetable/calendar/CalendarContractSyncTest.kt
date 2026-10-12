package com.example.timetable.calendar

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.timetable.data.calendar.CalendarContractClient
import com.example.timetable.data.calendar.CalendarPermissionChecker
import com.example.timetable.domain.calendar.CalendarEventSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 系统日历真实读写路径。需要先授予 READ_CALENDAR / WRITE_CALENDAR ——
 * 测试用 shell 授权而不是 GrantPermissionRule，避免额外引入 androidx.test:rules。
 *
 * 注意：权限**被拒**的真实行为无法在本测试内验证（pm revoke 会杀掉被测进程），
 * 该项以手工步骤登记进 docs/device-verification.md。
 */
@RunWith(AndroidJUnit4::class)
class CalendarContractSyncTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var checker: CalendarPermissionChecker

    private lateinit var client: CalendarContractClient

    @Before
    fun grantCalendarPermissions() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant " + context.packageName + " android.permission.READ_CALENDAR")
        device.executeShellCommand("pm grant " + context.packageName + " android.permission.WRITE_CALENDAR")
        checker = CalendarPermissionChecker(context)
        client = CalendarContractClient(context, checker)
    }

    @Test
    fun courseCalendarIsCreatedAndReused() {
        assumeTrue("未授予日历权限，跳过", checker.hasAllPermissions())

        val first = runBlocking { client.ensureCourseCalendar() }
        val second = runBlocking { client.ensureCourseCalendar() }

        assertNotNull("应能创建或找到「课程表」专用日历", first)
        assertEquals("重复调用必须复用同一个日历", first, second)
    }

    @Test
    fun insertedEventIsQueryableInOurCalendar() {
        assumeTrue("未授予日历权限，跳过", checker.hasAllPermissions())
        val calendarId = runBlocking { client.ensureCourseCalendar() }
        assertNotNull(calendarId)

        val spec = CalendarEventSpec(
            segmentStartWeek = 1,
            segmentEndWeek = 4,
            title = "自动化测试课程",
            location = "测试教室",
            description = "instrumentation",
            startMillis = System.currentTimeMillis() + 86_400_000L,
            endMillis = System.currentTimeMillis() + 86_400_000L + 2_700_000L,
            timeZone = "UTC",
            rrule = "FREQ=WEEKLY;BYDAY=MO;COUNT=4",
            contentHash = "test-hash",
        )

        val eventId = client.insertEvent(calendarId!!, spec)
        assertNotNull("事件应插入成功", eventId)
        try {
            val ids = client.eventIdsInCalendar(calendarId)
            assertTrue("刚插入的事件应能被按 CALENDAR_ID 查到", ids.contains(eventId!!))
        } finally {
            client.deleteEvent(eventId!!)
        }

        assertTrue(
            "删除后不应再出现在本应用日历里",
            !client.eventIdsInCalendar(calendarId).contains(eventId),
        )
    }

    @Test
    fun permissionCheckerReflectsGrantedState() {
        assertTrue(checker.hasReadPermission())
        assertTrue(checker.hasWritePermission())
        assertTrue(checker.hasAllPermissions())
    }
}
