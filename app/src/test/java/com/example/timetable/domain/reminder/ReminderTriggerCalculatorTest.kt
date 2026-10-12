package com.example.timetable.domain.reminder

import com.example.timetable.domain.model.ReminderSchedule
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTriggerCalculatorTest {

    private val zone: ZoneId = ZoneOffset.UTC

    /** 2026-09-07 是周一。 */
    private val monday = LocalDate.of(2026, 9, 7)

    private fun millis(date: String, time: String): Long =
        java.time.LocalDateTime.parse(date + "T" + time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun triggerIsStartTimeMinusAdvance() {
        val trigger = next(
            schedule(weekday = 1, weeks = listOf(1), advanceMinutes = 15),
            now = millis("2026-09-06", "00:00"),
        )

        assertEquals(millis("2026-09-07", "07:45"), trigger)
    }

    @Test
    fun alreadyPassedOccurrenceIsSkipped() {
        val trigger = next(
            schedule(weekday = 1, weeks = listOf(1), advanceMinutes = 15),
            now = millis("2026-09-07", "08:00"),
        )

        assertNull("本周已过且没有后续生效周时应返回 null", trigger)
    }

    @Test
    fun triggerRollsToNextEffectiveWeek() {
        val trigger = next(
            schedule(weekday = 1, weeks = listOf(1, 2), advanceMinutes = 15),
            now = millis("2026-09-07", "08:00"),
        )

        assertEquals(millis("2026-09-14", "07:45"), trigger)
    }

    @Test
    fun weekdayOffsetMapsSundayToWeekEnd() {
        val trigger = next(
            schedule(weekday = 7, weeks = listOf(1), advanceMinutes = 0),
            now = millis("2026-09-06", "00:00"),
        )

        assertEquals(millis("2026-09-13", "08:00"), trigger)
    }

    @Test
    fun ruleWeeksIntersectCourseWeeks() {
        val trigger = next(
            schedule(weekday = 1, weeks = listOf(2), courseWeeks = listOf(1, 2, 3), advanceMinutes = 0),
            now = millis("2026-09-01", "00:00"),
        )

        assertEquals("规则周次与课程周次取交集后只剩第 2 周", millis("2026-09-14", "08:00"), trigger)
    }

    @Test
    fun emptyRuleWeeksFollowCourseWeeks() {
        val trigger = next(
            schedule(weekday = 1, weeks = emptyList(), courseWeeks = listOf(3), advanceMinutes = 0),
            now = millis("2026-09-01", "00:00"),
        )

        assertEquals(millis("2026-09-21", "08:00"), trigger)
    }

    @Test
    fun emptyCourseWeeksYieldsNoTrigger() {
        val trigger = next(
            schedule(weekday = 1, weeks = listOf(1), courseWeeks = emptyList(), advanceMinutes = 0),
            now = millis("2026-09-01", "00:00"),
        )

        assertNull(trigger)
    }

    @Test
    fun missingStartTimeYieldsNoTrigger() {
        val trigger = next(
            schedule(weekday = 1, weeks = listOf(1), startTime = null),
            now = millis("2026-09-01", "00:00"),
        )

        assertNull(trigger)
    }

    @Test
    fun malformedStartTimeYieldsNoTrigger() {
        val trigger = next(
            schedule(weekday = 1, weeks = listOf(1), startTime = "上午"),
            now = millis("2026-09-01", "00:00"),
        )

        assertNull(trigger)
    }

    @Test
    fun advanceAcrossMidnightFallsOnPreviousDay() {
        val trigger = next(
            schedule(weekday = 1, weeks = listOf(1), startTime = "00:30", advanceMinutes = 60),
            now = millis("2026-09-01", "00:00"),
        )

        assertEquals(millis("2026-09-06", "23:30"), trigger)
    }

    @Test
    fun zeroAdvanceFiresExactlyAtStartTime() {
        val trigger = next(
            schedule(weekday = 1, weeks = listOf(1), advanceMinutes = 0),
            now = millis("2026-09-07", "07:59"),
        )

        assertEquals(millis("2026-09-07", "08:00"), trigger)
    }

    private fun next(schedule: ReminderSchedule, now: Long): Long? =
        ReminderTriggerCalculator.nextTriggerAtMillis(schedule, now, zone)

    private fun schedule(
        weekday: Int,
        weeks: List<Int>,
        courseWeeks: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
        startTime: String? = "08:00",
        advanceMinutes: Int = 15,
    ) = ReminderSchedule(
        ruleId = 1L,
        courseId = 1L,
        courseName = "数据结构",
        location = "计算机楼 402",
        weekday = weekday,
        startSection = 1,
        startTime = startTime,
        timetableId = 1L,
        timetableName = "2026 秋",
        timetableStartDate = monday,
        advanceMinutes = advanceMinutes,
        enabled = true,
        ruleWeeks = weeks,
        courseWeeks = courseWeeks,
    )
}
