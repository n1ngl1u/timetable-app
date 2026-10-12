package com.example.timetable.calendar

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.timetable.core.common.DefaultDispatcherProvider
import com.example.timetable.core.time.SystemTimeProvider
import com.example.timetable.data.calendar.CalendarContractClient
import com.example.timetable.data.calendar.CalendarPermissionChecker
import com.example.timetable.data.local.db.AppDatabase
import com.example.timetable.data.local.db.Migrations
import com.example.timetable.data.local.entity.CourseEntity
import com.example.timetable.data.local.entity.TimetableEntity
import com.example.timetable.data.repository.CalendarRepositoryImpl
import com.example.timetable.domain.calendar.CalendarEventInsert
import com.example.timetable.domain.calendar.CalendarEventSpec
import com.example.timetable.domain.calendar.CalendarSyncPlan
import com.example.timetable.domain.calendar.CalendarSyncResult
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 5A 遗留项：删除课程后系统日历事件与 link 记录都必须消失。
 * 手工装配真实的 CalendarRepositoryImpl（不需要 Hilt），种子数据直接走 DAO。
 */
@RunWith(AndroidJUnit4::class)
class CalendarRepositoryDeleteEventsTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val dbName = "calendar-delete-test.db"

    private lateinit var database: AppDatabase

    private lateinit var client: CalendarContractClient

    private lateinit var repository: CalendarRepositoryImpl

    @Before
    fun setUp() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant " + context.packageName + " android.permission.READ_CALENDAR")
        device.executeShellCommand("pm grant " + context.packageName + " android.permission.WRITE_CALENDAR")

        context.deleteDatabase(dbName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .build()
        val checker = CalendarPermissionChecker(context)
        client = CalendarContractClient(context, checker)
        repository = CalendarRepositoryImpl(
            database = database,
            linkDao = database.calendarEventLinkDao(),
            client = client,
            permissionChecker = checker,
            timeProvider = SystemTimeProvider(),
            dispatchers = DefaultDispatcherProvider(),
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun deletingCourseRemovesCalendarEventAndLinkRow() {
        assumeTrue("未授予日历权限，跳过", CalendarPermissionChecker(context).hasAllPermissions())

        runBlocking {
            val timetableId = database.timetableDao().insert(
                TimetableEntity(
                    tableName = "删除测试课表",
                    startDate = LocalDate.of(2026, 9, 7),
                    semesterId = null,
                ),
            )
            database.courseDao().insertAll(
                listOf(
                    CourseEntity(
                        timetableId = timetableId,
                        name = "待删除课程",
                        teacher = "老师",
                        location = "测试教室",
                        weekday = 1,
                        startSection = 1,
                        endSection = 2,
                        weeks = listOf(1, 2, 3, 4),
                    ),
                ),
            )
            val courseId = database.courseDao().findByTimetable(timetableId).single().id

            val spec = CalendarEventSpec(
                segmentStartWeek = 1,
                segmentEndWeek = 4,
                title = "待删除课程",
                location = "测试教室",
                description = "instrumentation",
                startMillis = System.currentTimeMillis() + 86_400_000L,
                endMillis = System.currentTimeMillis() + 86_400_000L + 2_700_000L,
                timeZone = "UTC",
                rrule = "FREQ=WEEKLY;BYDAY=MO;COUNT=4",
                contentHash = "delete-test-hash",
            )
            val plan = CalendarSyncPlan(
                inserts = listOf(CalendarEventInsert(courseId, spec)),
                updates = emptyList(),
                deletes = emptyList(),
                unchanged = emptyList(),
                skippedCourses = emptyList(),
            )
            val applied = repository.applyChanges(plan)
            assertTrue("写入计划应成功: " + applied, applied is CalendarSyncResult.Synced)

            val linksBefore = database.calendarEventLinkDao().findByCourseIds(listOf(courseId))
            assertTrue("link 记录应已写入", linksBefore.isNotEmpty())
            val eventId = linksBefore.single().calendarEventId
            val calendarId = client.ensureCourseCalendar()
            assertNotNull(calendarId)
            assertTrue("事件应存在于本应用日历", client.eventIdsInCalendar(calendarId!!).contains(eventId))

            val deleted = repository.deleteEventsForCourse(courseId)
            assertTrue("删除应成功: " + deleted, deleted is CalendarSyncResult.Synced)

            assertTrue(
                "系统日历事件必须消失",
                !client.eventIdsInCalendar(calendarId).contains(eventId),
            )
            assertTrue(
                "link 记录必须消失",
                database.calendarEventLinkDao().findByCourseIds(listOf(courseId)).isEmpty(),
            )
        }
    }

    @Test
    fun deletingCourseWithoutLinksIsNoOp() {
        assumeTrue("未授予日历权限，跳过", CalendarPermissionChecker(context).hasAllPermissions())

        runBlocking {
            val result = repository.deleteEventsForCourse(NON_EXISTENT_COURSE_ID)

            assertTrue(result is CalendarSyncResult.Synced)
        }
    }

    private companion object {
        const val NON_EXISTENT_COURSE_ID = 999_999L
    }
}
