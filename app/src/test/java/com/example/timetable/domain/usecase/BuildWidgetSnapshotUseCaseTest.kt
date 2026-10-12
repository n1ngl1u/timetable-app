package com.example.timetable.domain.usecase

import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable
import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimeProvider
import com.example.timetable.testing.FakeTimetableRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildWidgetSnapshotUseCaseTest {

    private val monday = LocalDate.of(2026, 9, 7)

    private val timetableRepository = FakeTimetableRepository()

    private val settingsRepository = FakeSettingsRepository()

    private fun useCase(today: LocalDate) = BuildWidgetSnapshotUseCase(
        timetableRepository = timetableRepository,
        settingsRepository = settingsRepository,
        timeProvider = FakeTimeProvider(today = today),
    )

    /** 多课表语义下必须显式指定活跃课表，不再有「回退第一张」的隐式行为。 */
    private suspend fun seedActive(timetable: Timetable): Long {
        val id = timetableRepository.upsertTimetable(timetable)
        settingsRepository.setActiveTimetable(id)
        return id
    }

    @Test
    fun todayReturnsCoursesOfCurrentWeekdayAndWeek() = runTest {
        seedActive(
            timetable(
                courses = listOf(
                    course(name = "数据结构", weekday = 3, startSection = 1, endSection = 2, weeks = listOf(1, 2)),
                    course(name = "体育", weekday = 5, startSection = 3, endSection = 4, weeks = listOf(1)),
                ),
            ),
        )

        val snapshot = useCase(monday.plusDays(2)).today()

        assertEquals(3, snapshot.weekday)
        assertEquals(1, snapshot.weekIndex)
        assertEquals(listOf("数据结构"), snapshot.items.map { it.name })
    }

    @Test
    fun courseNotActiveInCurrentWeekIsExcluded() = runTest {
        seedActive(
            timetable(
                courses = listOf(
                    course(name = "单周课", weekday = 3, weeks = listOf(1, 3, 5)),
                    course(name = "双周课", weekday = 3, weeks = listOf(2, 4, 6)),
                ),
            ),
        )

        val week1 = useCase(monday.plusDays(2)).today()
        val week2 = useCase(monday.plusDays(9)).today()

        assertEquals(listOf("单周课"), week1.items.map { it.name })
        assertEquals(listOf("双周课"), week2.items.map { it.name })
    }

    @Test
    fun weekIndexAdvancesEverySevenDays() = runTest {
        seedActive(timetable(courses = emptyList()))
        assertEquals(3, useCase(monday.plusDays(15)).today().weekIndex)
    }

    @Test
    fun weekIndexBeforeStartDateClampsToOne() = runTest {
        seedActive(timetable(courses = emptyList()))
        assertEquals(1, useCase(monday.minusDays(3)).today().weekIndex)
    }

    @Test
    fun weekReturnsSevenColumnsInWeekdayOrder() = runTest {
        seedActive(timetable(courses = listOf(course(name = "周日课", weekday = 7, weeks = listOf(1)))))

        val snapshot = useCase(monday).week()

        assertEquals(7, snapshot.days.size)
        assertEquals((1..7).toList(), snapshot.days.map { it.weekday })
        assertEquals(listOf("周日课"), snapshot.days.last().items.map { it.name })
        assertTrue(snapshot.hasCourses)
    }

    @Test
    fun missingTimetableYieldsEmptySnapshot() = runTest {
        val today = useCase(monday).today()
        val week = useCase(monday).week()

        assertFalse(today.hasCourses)
        assertEquals(0, today.weekIndex)
        // 2026-09-07 是周一，无课表时 weekday 仍应如实反映当天
        assertEquals(1, today.weekday)
        assertFalse(week.hasCourses)
        assertEquals(0, week.weekIndex)
    }

    @Test
    fun timeTextPrefersTimeSlots() = runTest {
        seedActive(
            timetable(
                timeSlots = listOf(
                    TimeSlot(number = 1, startTime = "08:00", endTime = "08:45"),
                    TimeSlot(number = 2, startTime = "08:55", endTime = "09:40"),
                ),
                courses = listOf(course(name = "数据结构", weekday = 3, startSection = 1, endSection = 2)),
            ),
        )

        assertEquals("08:00-09:40", useCase(monday.plusDays(2)).today().items.first().timeText)
    }

    @Test
    fun timeTextFallsBackToSectionsWhenSlotsMissing() = runTest {
        seedActive(
            timetable(
                timeSlots = emptyList(),
                courses = listOf(
                    course(name = "两节连排", weekday = 3, startSection = 1, endSection = 2),
                    course(name = "单节", weekday = 3, startSection = 5, endSection = 5),
                ),
            ),
        )

        val items = useCase(monday.plusDays(2)).today().items
        assertEquals(listOf("第1-2节", "第5节"), items.map { it.timeText })
    }

    @Test
    fun activeTimetableIdWinsOverOtherTimetables() = runTest {
        timetableRepository.upsertTimetable(timetable(tableName = "第一张", courses = emptyList()))
        val second = seedActive(
            timetable(
                tableName = "第二张",
                courses = listOf(course(name = "操作系统", weekday = 3, weeks = listOf(1))),
            ),
        )

        val snapshot = useCase(monday.plusDays(2)).today()

        assertEquals("第二张", snapshot.tableName)
        assertEquals(listOf("操作系统"), snapshot.items.map { it.name })
        assertTrue(second > 0L)
    }

    @Test
    fun unknownActiveIdYieldsEmptySnapshotInsteadOfFallingBack() = runTest {
        timetableRepository.upsertTimetable(
            timetable(
                tableName = "存在的课表",
                courses = listOf(course(name = "操作系统", weekday = 3, weeks = listOf(1))),
            ),
        )
        settingsRepository.setActiveTimetable(999L)

        val snapshot = useCase(monday.plusDays(2)).today()

        assertEquals("", snapshot.tableName)
        assertFalse(snapshot.hasCourses)
    }

    private fun timetable(
        tableName: String = "2026 秋",
        timeSlots: List<TimeSlot> = emptyList(),
        courses: List<Course> = emptyList(),
    ) = Timetable(
        tableName = tableName,
        startDate = monday,
        timeSlots = timeSlots,
        courses = courses,
    )

    private fun course(
        name: String,
        weekday: Int,
        startSection: Int = 1,
        endSection: Int = 2,
        weeks: List<Int> = listOf(1),
    ) = Course(
        name = name,
        weekday = weekday,
        startSection = startSection,
        endSection = endSection,
        weeks = weeks,
    )
}
