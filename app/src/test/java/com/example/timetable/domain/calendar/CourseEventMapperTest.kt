package com.example.timetable.domain.calendar

import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseEventMapperTest {

    private val zone: ZoneId = ZoneOffset.UTC

    /** 2026-09-07 是周一。 */
    private val startDate = LocalDate.of(2026, 9, 7)

    @Test
    fun mapsTitleLocationAndTimeSlotRange() {
        val mapping = map(course(weekday = 1, startSection = 1, endSection = 2, weeks = listOf(1)))

        val spec = (mapping as CourseEventMapping.Mapped).specs.single()
        assertEquals("数据结构", spec.title)
        assertEquals("计算机楼 402", spec.location)
        assertEquals(millis("2026-09-07T08:00"), spec.startMillis)
        assertEquals(millis("2026-09-07T09:40"), spec.endMillis)
        assertEquals("UTC", spec.timeZone)
    }

    @Test
    fun contiguousWeeksProduceSingleRrule() {
        val mapping = map(course(weeks = (1..16).toList())) as CourseEventMapping.Mapped

        val spec = mapping.specs.single()
        assertEquals(1, spec.segmentStartWeek)
        assertEquals(16, spec.segmentEndWeek)
        assertEquals("FREQ=WEEKLY;BYDAY=MO;COUNT=16", spec.rrule)
    }

    @Test
    fun discreteSingleWeeksProduceOneEventPerWeek() {
        val mapping = map(course(weeks = listOf(1, 3, 5, 7))) as CourseEventMapping.Mapped

        assertEquals(4, mapping.specs.size)
        assertEquals(listOf(1, 3, 5, 7), mapping.specs.map { it.segmentStartWeek })
        assertTrue(mapping.specs.all { it.rrule == "FREQ=WEEKLY;BYDAY=MO;COUNT=1" })
    }

    @Test
    fun mixedWeeksSplitIntoContiguousRuns() {
        val mapping = map(course(weeks = listOf(1, 2, 3, 7, 8, 12))) as CourseEventMapping.Mapped

        assertEquals(listOf(1 to 3, 7 to 8, 12 to 12), mapping.specs.map { it.segmentStartWeek to it.segmentEndWeek })
        assertEquals(
            listOf("COUNT=3", "COUNT=2", "COUNT=1"),
            mapping.specs.map { it.rrule!!.substringAfterLast(";") },
        )
    }

    @Test
    fun weekdayMapsToCorrectByDayToken() {
        assertEquals("FREQ=WEEKLY;BYDAY=SU;COUNT=2", CourseEventMapper.buildRrule(7, 2))
        assertEquals("FREQ=WEEKLY;BYDAY=WE;COUNT=1", CourseEventMapper.buildRrule(3, 1))
    }

    /** 跨年：第 18 周的周一落在 2027-01-04。 */
    @Test
    fun weekEighteenCrossesIntoNextYear() {
        assertEquals(LocalDate.of(2027, 1, 4), CourseEventMapper.dateOf(startDate, 1, 18))

        val mapping = map(course(weekday = 1, weeks = listOf(18))) as CourseEventMapping.Mapped

        val spec = mapping.specs.single()
        assertEquals(millis("2027-01-04T08:00"), spec.startMillis)
        assertEquals(millis("2027-01-04T09:40"), spec.endMillis)
    }

    @Test
    fun missingTimeSlotSkipsCourseWithoutThrowing() {
        val timetable = timetable(timeSlots = emptyList())

        val mapping = CourseEventMapper.map(timetable, course(weeks = listOf(1)), zone)

        val skipped = mapping as CourseEventMapping.Skipped
        assertEquals(CalendarSkipReason.MISSING_TIME_SLOT, skipped.reason)
    }

    @Test
    fun unparsableTimeSlotSkipsCourse() {
        val timetable = timetable(
            timeSlots = listOf(TimeSlot(number = 1, startTime = "上午", endTime = "09:40")),
        )

        val mapping = CourseEventMapper.map(timetable, course(weeks = listOf(1)), zone)

        assertEquals(
            CalendarSkipReason.MISSING_TIME_SLOT,
            (mapping as CourseEventMapping.Skipped).reason,
        )
    }

    @Test
    fun emptyWeeksSkipCourse() {
        val mapping = map(course(weeks = emptyList()))

        assertEquals(
            CalendarSkipReason.NO_SCHEDULED_WEEKS,
            (mapping as CourseEventMapping.Skipped).reason,
        )
    }

    @Test
    fun contentHashIsStableForIdenticalInput() {
        val first = (map(course(weeks = listOf(1))) as CourseEventMapping.Mapped).specs.single()
        val second = (map(course(weeks = listOf(1))) as CourseEventMapping.Mapped).specs.single()

        assertEquals(first.contentHash, second.contentHash)
    }

    @Test
    fun contentHashChangesWhenLocationChanges() {
        val base = (map(course(weeks = listOf(1), location = "A")) as CourseEventMapping.Mapped).specs.single()
        val moved = (map(course(weeks = listOf(1), location = "B")) as CourseEventMapping.Mapped).specs.single()

        assertNotEquals(base.contentHash, moved.contentHash)
    }

    @Test
    fun contentHashIgnoresCourseId() {
        val first = (map(course(id = 1L, weeks = listOf(1))) as CourseEventMapping.Mapped).specs.single()
        val second = (map(course(id = 999L, weeks = listOf(1))) as CourseEventMapping.Mapped).specs.single()

        assertEquals("内容相同但 courseId 不同不应触发重写", first.contentHash, second.contentHash)
    }

    @Test
    fun contentHashIsSha256Hex() {
        val spec = (map(course(weeks = listOf(1))) as CourseEventMapping.Mapped).specs.single()

        assertEquals(64, spec.contentHash.length)
        assertTrue(spec.contentHash.all { it in "0123456789abcdef" })
        assertNull(spec.contentHash.firstOrNull { it !in "0123456789abcdef" })
    }

    @Test
    fun contiguousRunsHandlesEmptyAndUnsortedInput() {
        assertTrue(CourseEventMapper.contiguousRuns(emptyList()).isEmpty())
        assertEquals(
            listOf(1..3, 7..8),
            CourseEventMapper.contiguousRuns(listOf(8, 3, 1, 7, 2)),
        )
    }

    private fun millis(isoLocal: String): Long =
        LocalDateTime.parse(isoLocal).atZone(zone).toInstant().toEpochMilli()

    private fun map(course: Course): CourseEventMapping =
        CourseEventMapper.map(timetable(), course, zone)

    private fun timetable(
        timeSlots: List<TimeSlot> = listOf(
            TimeSlot(number = 1, startTime = "08:00", endTime = "08:45"),
            TimeSlot(number = 2, startTime = "08:55", endTime = "09:40"),
        ),
    ) = Timetable(
        tableName = "2026 秋",
        startDate = startDate,
        timeSlots = timeSlots,
    )

    private fun course(
        id: Long = 1L,
        weekday: Int = 1,
        startSection: Int = 1,
        endSection: Int = 2,
        weeks: List<Int> = listOf(1),
        location: String = "计算机楼 402",
    ) = Course(
        id = id,
        name = "数据结构",
        teacher = "王强",
        location = location,
        weekday = weekday,
        startSection = startSection,
        endSection = endSection,
        weeks = weeks,
    )
}
