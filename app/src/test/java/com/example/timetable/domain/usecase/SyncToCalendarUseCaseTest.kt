package com.example.timetable.domain.usecase

import com.example.timetable.domain.calendar.CalendarEventSpec
import com.example.timetable.domain.calendar.CalendarEventLink
import com.example.timetable.domain.calendar.CalendarSkipReason
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.domain.calendar.CourseEventMapper
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable
import com.example.timetable.testing.FakeCalendarRepository
import com.example.timetable.testing.FakeDispatcherProvider
import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimetableRepository
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncToCalendarUseCaseTest {

    private val zone = ZoneOffset.UTC

    private val startDate = LocalDate.of(2026, 9, 7)

    private val timetables = FakeTimetableRepository()

    private val settings = FakeSettingsRepository()

    private fun useCase(repository: FakeCalendarRepository) = SyncToCalendarUseCase(
        calendarRepository = repository,
        timetableRepository = timetables,
        settingsRepository = settings,
        dispatchers = FakeDispatcherProvider(),
    )

    @Test
    fun deniedPermissionSkipsWithoutTouchingRepository() = runTest {
        val repository = FakeCalendarRepository(permissionsGranted = false)
        seedActiveTimetable()

        val result = useCase(repository).invoke(zone)

        assertEquals(
            CalendarSyncResult.Skipped(CalendarSkipReason.PERMISSION_DENIED, "未授予日历权限，已降级为仅本地展示"),
            result,
        )
        assertTrue(repository.appliedPlans.isEmpty())
        assertEquals(0, repository.pruneCallCount)
    }

    @Test
    fun missingActiveTimetableSkips() = runTest {
        val repository = FakeCalendarRepository()

        val result = useCase(repository).invoke(zone)

        assertEquals(
            CalendarSyncResult.Skipped(CalendarSkipReason.NO_ACTIVE_TIMETABLE),
            result,
        )
    }

    @Test
    fun emptyTimetableSkips() = runTest {
        val repository = FakeCalendarRepository()
        val id = timetables.upsertTimetable(timetable(courses = emptyList()))
        settings.setActiveTimetable(id)

        val result = useCase(repository).invoke(zone)

        assertEquals(CalendarSyncResult.Skipped(CalendarSkipReason.NO_COURSES), result)
    }

    @Test
    fun firstSyncInsertsOneEventPerContiguousSegment() = runTest {
        val repository = FakeCalendarRepository()
        seedActiveTimetable()

        val result = useCase(repository).invoke(zone) as CalendarSyncResult.Synced

        assertEquals(1, result.outcome.inserted)
        assertEquals(1, repository.pruneCallCount)
        assertEquals(1, repository.appliedPlans.size)
    }

    @Test
    fun secondSyncWithSameContentIsNoOp() = runTest {
        val repository = FakeCalendarRepository()
        val timetableId = seedActiveTimetable()
        val timetable = timetables.timetable(timetableId)!!
        val course = timetable.courses.first()
        val spec = (CourseEventMapper.map(timetable, course, zone) as com.example.timetable.domain.calendar.CourseEventMapping.Mapped)
            .specs.single()
        repository.links += link(spec, eventId = 77L, courseId = course.id)

        val result = useCase(repository).invoke(zone) as CalendarSyncResult.Synced

        assertEquals("内容未变不应重写", 0, result.outcome.inserted)
        assertEquals(0, result.outcome.updated)
        assertEquals(1, result.outcome.unchanged)
        assertEquals(0, repository.appliedPlans.single().totalChanges)
    }

    @Test
    fun locationChangeProducesUpdate() = runTest {
        val repository = FakeCalendarRepository()
        val timetableId = seedActiveTimetable()
        val original = timetables.timetable(timetableId)!!
        val originalCourse = original.courses.first()
        val originalSpec = (CourseEventMapper.map(original, originalCourse, zone) as com.example.timetable.domain.calendar.CourseEventMapping.Mapped)
            .specs.single()
        repository.links += link(originalSpec, eventId = 88L, courseId = originalCourse.id)

        timetables.upsertTimetable(
            original.copy(
                id = timetableId,
                courses = listOf(originalCourse.copy(location = "教三 201")),
            ),
        )

        val result = useCase(repository).invoke(zone) as CalendarSyncResult.Synced

        assertEquals(1, result.outcome.updated)
        assertEquals(0, result.outcome.inserted)
        assertEquals(88L, repository.appliedPlans.single().updates.single().calendarEventId)
    }

    @Test
    fun missingTimeSlotIsReportedAsSkippedCourse() = runTest {
        val repository = FakeCalendarRepository()
        val id = timetables.upsertTimetable(timetable(timeSlots = emptyList()))
        settings.setActiveTimetable(id)

        val result = useCase(repository).invoke(zone) as CalendarSyncResult.Synced

        assertEquals(1, result.outcome.skippedCount)
        assertEquals(
            CalendarSkipReason.MISSING_TIME_SLOT,
            result.outcome.skippedCourses.single().reason,
        )
    }

    @Test
    fun onlyActiveTimetableIsSynced() = runTest {
        val repository = FakeCalendarRepository()
        timetables.upsertTimetable(timetable(tableName = "旁听", courses = emptyList()))
        val activeId = seedActiveTimetable()
        settings.setActiveTimetable(activeId)

        useCase(repository).invoke(zone)

        assertEquals(1, repository.appliedPlans.single().inserts.size)
    }

    private suspend fun seedActiveTimetable(): Long {
        val id = timetables.upsertTimetable(timetable())
        settings.setActiveTimetable(id)
        return id
    }

    private fun link(spec: CalendarEventSpec, eventId: Long, courseId: Long) = CalendarEventLink(
        courseId = courseId,
        calendarEventId = eventId,
        segmentStartWeek = spec.segmentStartWeek,
        segmentEndWeek = spec.segmentEndWeek,
        contentHash = spec.contentHash,
        syncedAt = 0L,
    )

    private fun timetable(
        tableName: String = "2026 秋",
        timeSlots: List<TimeSlot> = listOf(
            TimeSlot(number = 1, startTime = "08:00", endTime = "08:45"),
            TimeSlot(number = 2, startTime = "08:55", endTime = "09:40"),
        ),
        courses: List<Course> = listOf(
            Course(
                id = 1L,
                name = "数据结构",
                teacher = "王强",
                location = "计算机楼 402",
                weekday = 1,
                startSection = 1,
                endSection = 2,
                weeks = listOf(1, 2, 3, 4),
            ),
        ),
    ) = Timetable(
        tableName = tableName,
        startDate = startDate,
        timeSlots = timeSlots,
        courses = courses,
    )
}
