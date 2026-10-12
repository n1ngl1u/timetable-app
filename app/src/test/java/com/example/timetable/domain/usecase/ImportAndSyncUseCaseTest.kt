package com.example.timetable.domain.usecase

import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.data.adapter.json.CourseParseException
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.domain.importer.ImportIoException
import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.TimetableFetchRequest
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable
import java.time.LocalDate
import com.example.timetable.testing.FakeCalendarRepository
import com.example.timetable.testing.FakeDispatcherProvider
import com.example.timetable.testing.FakeSchoolAdapter
import com.example.timetable.testing.FakeSchoolRegistry
import com.example.timetable.testing.FakeSchoolRepository
import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimeProvider
import com.example.timetable.testing.FakeTimetableRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAndSyncUseCaseTest {

    private val dispatchers = FakeDispatcherProvider()

    private val timetables = FakeTimetableRepository()

    private val settings = FakeSettingsRepository()

    private val calendars = FakeCalendarRepository()

    /**
     * FakeSchoolAdapter.parse 不会真的解析 payload，它返回预设的 Timetable。
     * 所以课程数断言必须由这份预设决定，不能指望 payload 里的课程数。
     */
    private val parsedTimetable = Timetable(
        tableName = "测试课表",
        startDate = LocalDate.of(2026, 9, 7),
        timeSlots = listOf(
            TimeSlot(number = 1, startTime = "08:00", endTime = "08:45"),
            TimeSlot(number = 2, startTime = "08:55", endTime = "09:40"),
        ),
        courses = listOf(
            Course(
                id = 1L,
                name = "数据结构",
                teacher = "王强",
                location = "计算机楼 402",
                weekday = 1,
                startSection = 1,
                endSection = 2,
                weeks = listOf(1, 2),
            ),
            Course(
                id = 2L,
                name = "高等数学",
                teacher = "李敏",
                location = "教一 101",
                weekday = 3,
                startSection = 1,
                endSection = 2,
                weeks = listOf(1, 2),
            ),
        ),
    )

    private fun useCase(
        adapters: List<FakeSchoolAdapter> = listOf(FakeSchoolAdapter(parsed = parsedTimetable)),
        repository: FakeSchoolRepository = FakeSchoolRepository(adapters),
    ): ImportAndSyncUseCase {
        val registry = FakeSchoolRegistry(adapters)
        return ImportAndSyncUseCase(
            importTimetable = ImportTimetableUseCase(
                schoolRegistry = registry,
                schoolRepository = repository,
                timetableRepository = timetables,
                settingsRepository = settings,
                perfTracer = PerfTracer(FakeTimeProvider()),
                dispatchers = dispatchers,
            ),
            syncToCalendar = SyncToCalendarUseCase(
                calendarRepository = calendars,
                timetableRepository = timetables,
                settingsRepository = settings,
                dispatchers = dispatchers,
            ),
            schoolRegistry = registry,
            schoolRepository = repository,
            settingsRepository = settings,
            dispatchers = dispatchers,
        )
    }

    @Test
    fun prepareUsesProvidedPayloadWithoutFetching() = runTest {
        val adapter = FakeSchoolAdapter(parsed = parsedTimetable)
        val useCase = useCase(listOf(adapter))

        val preparation = useCase.prepare(request(payload = SAMPLE_JSON))

        assertTrue(preparation is ImportPreparation.Prepared)
        assertEquals(2, (preparation as ImportPreparation.Prepared).newCourseCount)
        assertEquals("载荷已经给了就不该再读一次", 0, adapter.fetchCount)
    }

    @Test
    fun prepareFetchesPayloadWhenOnlyUriIsGiven() = runTest {
        val adapter = FakeSchoolAdapter()
        val useCase = useCase(listOf(adapter))

        val preparation = useCase.prepare(
            ImportRequest(
                schoolId = FakeSchoolAdapter.SCHOOL_ID,
                fetchRequest = TimetableFetchRequest(sourceUri = "content://timetable/sample.json"),
            ),
        )

        assertTrue(preparation is ImportPreparation.Prepared)
        assertEquals(1, adapter.fetchCount)
    }

    @Test
    fun prepareReportsReadFailureWithItsReason() = runTest {
        val adapter = FakeSchoolAdapter()
        val useCase = useCase(
            adapters = listOf(adapter),
            repository = FakeSchoolRepository(
                adapters = listOf(adapter),
                fetchError = ImportIoException("课表文件不存在: /tmp/x.json"),
            ),
        )

        val preparation = useCase.prepare(
            ImportRequest(
                schoolId = FakeSchoolAdapter.SCHOOL_ID,
                fetchRequest = TimetableFetchRequest(sourceUri = "/tmp/x.json"),
            ),
        )

        assertEquals(
            ImportPreparation.Failure("课表文件不存在: /tmp/x.json"),
            preparation,
        )
    }

    /** 解析失败不在准备阶段拦，交给导入链路产出准确错误。 */
    @Test
    fun prepareMarksUnparsableInsteadOfGuessingTheError() = runTest {
        val adapter = FakeSchoolAdapter(parseError = CourseParseException("结构错误"))
        val useCase = useCase(listOf(adapter))

        val preparation = useCase.prepare(request(payload = "{ 不是 JSON"))

        assertTrue(preparation is ImportPreparation.Unparsable)
    }

    @Test
    fun commitSkipsCalendarSyncWhenSwitchIsOff() = runTest {
        val useCase = useCase()

        val outcome = useCase.commit(request(payload = SAMPLE_JSON))

        assertTrue(outcome is ImportOutcome.Success)
        assertEquals(null, (outcome as ImportOutcome.Success).calendarSync)
        assertTrue("开关没开就不得碰日历", calendars.appliedPlans.isEmpty())
        assertEquals(0, calendars.pruneCallCount)
    }

    @Test
    fun commitSyncsWhenSwitchIsOn() = runTest {
        settings.update { it.copy(calendarSyncEnabled = true) }
        val useCase = useCase()

        val outcome = useCase.commit(request(payload = SAMPLE_JSON))

        assertTrue(outcome is ImportOutcome.Success)
        val sync = (outcome as ImportOutcome.Success).calendarSync
        assertTrue("开关开着就必须真的走一次同步", sync is CalendarSyncResult.Synced)
        assertEquals(1, calendars.appliedPlans.size)
    }

    /** 基准链路：syncCalendar = false 时连设置都不读，返回的 calendarSync 恒为 null。 */
    @Test
    fun commitWithSyncDisabledDoesNotTouchCalendarAtAll() = runTest {
        settings.update { it.copy(calendarSyncEnabled = true) }
        val useCase = useCase()

        val outcome = useCase.commit(request(payload = SAMPLE_JSON), syncCalendar = false)

        assertEquals(null, (outcome as ImportOutcome.Success).calendarSync)
        assertTrue(calendars.appliedPlans.isEmpty())
        assertEquals(0, calendars.pruneCallCount)
    }

    @Test
    fun commitReportsImportFailureAndDoesNotSync() = runTest {
        settings.update { it.copy(calendarSyncEnabled = true) }
        val adapter = FakeSchoolAdapter(
            parseError = CourseParseException("字段缺失: courses[0].name", field = "courses[0].name"),
        )
        val useCase = useCase(listOf(adapter))

        val outcome = useCase.commit(request(payload = "{}"))

        assertTrue(outcome is ImportOutcome.Failure)
        assertEquals("字段缺失: courses[0].name", (outcome as ImportOutcome.Failure).message)
        assertTrue("导入失败绝不能顺手写日历", calendars.appliedPlans.isEmpty())
    }

    private fun request(payload: String) = ImportRequest(
        schoolId = FakeSchoolAdapter.SCHOOL_ID,
        payload = payload,
    )

    private companion object {
        val SAMPLE_JSON = """
            {
              "tableName": "测试课表",
              "startDate": "2026-09-07",
              "timeSlots": [
                {"number": 1, "startTime": "08:00", "endTime": "08:45"},
                {"number": 2, "startTime": "08:55", "endTime": "09:40"}
              ],
              "courses": [
                {"name": "数据结构", "teacher": "王强", "location": "计算机楼 402",
                 "weekday": 1, "startSection": 1, "endSection": 2, "weeks": [1, 2, 3, 4]},
                {"name": "高等数学", "teacher": "李敏", "location": "教一 101",
                 "weekday": 3, "startSection": 3, "endSection": 4, "weeks": [1, 2, 3, 4]}
              ]
            }
        """.trimIndent()
    }
}
