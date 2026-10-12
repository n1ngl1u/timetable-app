package com.example.timetable.domain.usecase

import com.example.timetable.domain.calendar.CalendarSkipReason
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.testing.FakeCalendarRepository
import com.example.timetable.testing.FakeDispatcherProvider
import com.example.timetable.testing.FakeTimetableRepository
import com.example.timetable.testing.populatedTimetable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteCourseUseCaseTest {

    private val timetables = FakeTimetableRepository()

    private fun useCase(calendar: FakeCalendarRepository) = DeleteCourseUseCase(
        calendarRepository = calendar,
        timetableRepository = timetables,
        dispatchers = FakeDispatcherProvider(),
    )

    @Test
    fun calendarIsClearedBeforeCourseIsDeleted() = runTest {
        val calendar = FakeCalendarRepository()
        val timetableId = timetables.upsertTimetable(populatedTimetable("2026 秋", courseCount = 2))
        val courseId = timetables.timetable(timetableId)!!.courses.first().id

        val result = useCase(calendar).invoke(courseId)

        assertEquals(DeleteCourseResult.Deleted, result)
        assertEquals(listOf(courseId), calendar.deletedCourseIds)
        assertEquals(listOf(courseId), timetables.deletedCourseIds)
        assertEquals(1, timetables.timetable(timetableId)!!.courses.size)
    }

    @Test
    fun calendarWriteFailureAbortsCourseDeletion() = runTest {
        val calendar = FakeCalendarRepository(
            deleteResult = CalendarSyncResult.Failed("系统日历写入失败"),
        )
        val timetableId = timetables.upsertTimetable(populatedTimetable("2026 秋", courseCount = 1))
        val courseId = timetables.timetable(timetableId)!!.courses.first().id

        val result = useCase(calendar).invoke(courseId)

        assertTrue(result is DeleteCourseResult.AbortedByCalendar)
        assertEquals("系统日历写入失败", (result as DeleteCourseResult.AbortedByCalendar).message)
        assertTrue("日历侧失败时课程必须保留，link 留着以便重试", timetables.deletedCourseIds.isEmpty())
        assertEquals(1, timetables.timetable(timetableId)!!.courses.size)
    }

    @Test
    fun revokedCalendarPermissionStillDeletesCourseButReportsResidue() = runTest {
        val calendar = FakeCalendarRepository(
            permissionsGranted = false,
            deleteResult = CalendarSyncResult.Skipped(CalendarSkipReason.PERMISSION_DENIED),
        )
        val timetableId = timetables.upsertTimetable(populatedTimetable("2026 秋", courseCount = 1))
        val courseId = timetables.timetable(timetableId)!!.courses.first().id

        val result = useCase(calendar).invoke(courseId)

        assertEquals(DeleteCourseResult.DeletedWithResidualCalendarEvents, result)
        assertEquals(listOf(courseId), timetables.deletedCourseIds)
    }

    @Test
    fun nonPermissionSkipStillDeletesCourse() = runTest {
        val calendar = FakeCalendarRepository(
            deleteResult = CalendarSyncResult.Skipped(CalendarSkipReason.NO_WRITABLE_CALENDAR),
        )
        val timetableId = timetables.upsertTimetable(populatedTimetable("2026 秋", courseCount = 1))
        val courseId = timetables.timetable(timetableId)!!.courses.first().id

        val result = useCase(calendar).invoke(courseId)

        assertEquals(DeleteCourseResult.Deleted, result)
        assertEquals(listOf(courseId), timetables.deletedCourseIds)
    }
}
