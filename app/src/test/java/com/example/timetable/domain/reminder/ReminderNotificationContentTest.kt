package com.example.timetable.domain.reminder

import com.example.timetable.domain.model.ReminderSchedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationContentTest {

    @Test
    fun titleIsCourseNameAndBodyJoinsLocationAndTime() {
        val content = ReminderNotificationContentFactory.create(schedule())

        assertEquals("数据结构", content.title)
        assertEquals("计算机楼 402 · 08:00", content.body)
    }

    @Test
    fun blankLocationIsOmitted() {
        val content = ReminderNotificationContentFactory.create(schedule(location = "   "))

        assertEquals("08:00", content.body)
    }

    @Test
    fun missingStartTimeIsOmitted() {
        val content = ReminderNotificationContentFactory.create(schedule(startTime = null))

        assertEquals("计算机楼 402", content.body)
    }

    @Test
    fun notificationIdIsStableForSameRule() {
        assertEquals(
            ReminderNotificationContentFactory.notificationIdFor(42L),
            ReminderNotificationContentFactory.notificationIdFor(42L),
        )
    }

    @Test
    fun differentRuleIdsProduceDifferentNotificationIds() {
        val ids = listOf(1L, 2L, 3L, 100L, 4_294_967_297L, 9_007_199_254_740_993L)
            .map { ReminderNotificationContentFactory.notificationIdFor(it) }

        assertEquals("不同 ruleId 不应折叠到同一个通知 id", ids.size, ids.toSet().size)
    }

    @Test
    fun highBitsAreFoldedInsteadOfTruncated() {
        // 仅做 toInt() 截断时这两个 id 会相同；折叠异或后必须不同
        assertNotEquals(
            ReminderNotificationContentFactory.notificationIdFor(1L),
            ReminderNotificationContentFactory.notificationIdFor(1L + (1L shl 32)),
        )
    }

    @Test
    fun contentCarriesStableNotificationId() {
        val first = ReminderNotificationContentFactory.create(schedule())
        val second = ReminderNotificationContentFactory.create(schedule())

        assertEquals(first.notificationId, second.notificationId)
        assertTrue(first.notificationId == ReminderNotificationContentFactory.notificationIdFor(1L))
    }

    private fun schedule(
        location: String = "计算机楼 402",
        startTime: String? = "08:00",
    ) = ReminderSchedule(
        ruleId = 1L,
        courseId = 1L,
        courseName = "数据结构",
        location = location,
        weekday = 1,
        startSection = 1,
        startTime = startTime,
        timetableId = 1L,
        timetableName = "2026 秋",
        timetableStartDate = LocalDate.of(2026, 9, 7),
        advanceMinutes = 15,
        enabled = true,
        ruleWeeks = emptyList(),
        courseWeeks = listOf(1),
    )
}
