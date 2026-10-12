package com.example.timetable.domain.usecase

import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.data.adapter.json.CourseParseException
import com.example.timetable.domain.importer.ImportIoException
import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.ImportResult
import com.example.timetable.domain.importer.LoginState
import com.example.timetable.domain.importer.SchoolCredentials
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.Timetable
import com.example.timetable.testing.FakeDispatcherProvider
import com.example.timetable.testing.FakeSchoolAdapter
import com.example.timetable.testing.FakeSchoolRegistry
import com.example.timetable.testing.FakeSchoolRepository
import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimeProvider
import com.example.timetable.testing.FakeTimetableRepository
import com.example.timetable.testing.SAMPLE_PAYLOAD
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportResultContractTest {

    private val credentials = SchoolCredentials(username = "u", password = "p")

    private val settings = FakeSettingsRepository()

    private fun useCase(
        adapter: FakeSchoolAdapter = FakeSchoolAdapter(),
        schoolRepository: FakeSchoolRepository = FakeSchoolRepository(),
        timetableRepository: FakeTimetableRepository = FakeTimetableRepository(),
        registry: FakeSchoolRegistry = FakeSchoolRegistry(listOf(adapter)),
    ) = ImportTimetableUseCase(
        schoolRegistry = registry,
        schoolRepository = schoolRepository,
        timetableRepository = timetableRepository,
        settingsRepository = settings,
        perfTracer = PerfTracer(FakeTimeProvider()),
        dispatchers = FakeDispatcherProvider(),
    )

    @Test
    fun unknownSchoolMapsToInvalidInput() = runTest {
        val result = useCase().invoke(ImportRequest(schoolId = "not-registered", payload = SAMPLE_PAYLOAD))
        assertTrue(result is ImportResult.InvalidInput)
    }

    @Test
    fun loginFailureMapsToSessionExpired() = runTest {
        val adapter = FakeSchoolAdapter()
        val result = useCase(
            adapter = adapter,
            schoolRepository = FakeSchoolRepository(loginState = LoginState.Failed("账号或密码错误")),
        ).invoke(
            ImportRequest(
                schoolId = adapter.schoolId,
                payload = SAMPLE_PAYLOAD,
                credentials = credentials,
            ),
        )
        assertTrue(result is ImportResult.SessionExpired)
    }

    @Test
    fun networkAdapterWithoutCredentialsMapsToSessionExpired() = runTest {
        val adapter = FakeSchoolAdapter(requiresNetwork = true)
        val result = useCase(adapter = adapter).invoke(ImportRequest(schoolId = adapter.schoolId))
        assertTrue(result is ImportResult.SessionExpired)
    }

    @Test
    fun ioFailureOnFetchMapsToNetworkFailure() = runTest {
        val adapter = FakeSchoolAdapter(fetchError = IOException("连接超时"))
        val result = useCase(adapter = adapter).invoke(ImportRequest(schoolId = adapter.schoolId))
        assertTrue(result is ImportResult.NetworkFailure)
        assertEquals("连接超时", (result as ImportResult.NetworkFailure).message)
    }

    @Test
    fun localIoFailureMapsToInvalidInput() = runTest {
        val adapter = FakeSchoolAdapter(fetchError = ImportIoException("课表文件不存在: /x.json"))
        val result = useCase(adapter = adapter).invoke(ImportRequest(schoolId = adapter.schoolId))
        assertTrue(result is ImportResult.InvalidInput)
    }

    @Test
    fun parseErrorWithoutFieldMapsToSchemaChanged() = runTest {
        val adapter = FakeSchoolAdapter(
            parseError = CourseParseException("courses[0].endSection 非法: 1 小于 startSection 3"),
        )
        val result = useCase(adapter = adapter).invoke(ImportRequest(schoolId = adapter.schoolId))
        assertTrue(result is ImportResult.SchemaChanged)
    }

    @Test
    fun parseErrorWithFieldMapsToMissingField() = runTest {
        val adapter = FakeSchoolAdapter(
            parseError = CourseParseException("字段缺失: courses[0].weekday", field = "courses[0].weekday"),
        )
        val result = useCase(adapter = adapter).invoke(ImportRequest(schoolId = adapter.schoolId))
        assertTrue(result is ImportResult.MissingField)
        assertEquals("courses[0].weekday", (result as ImportResult.MissingField).field)
    }

    @Test
    fun unexpectedExceptionMapsToInvalidInput() = runTest {
        val adapter = FakeSchoolAdapter(fetchError = IllegalStateException("boom"))
        val result = useCase(adapter = adapter).invoke(ImportRequest(schoolId = adapter.schoolId))
        assertTrue(result is ImportResult.InvalidInput)
    }

    @Test
    fun successReturnsTimetableIdAndCourseCount() = runTest {
        val adapter = FakeSchoolAdapter()
        val result = useCase(adapter = adapter).invoke(ImportRequest(schoolId = adapter.schoolId))
        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(1L, success.timetableId)
        assertEquals(1, success.courseCount)
    }

    @Test
    fun providedPayloadSkipsAdapterFetch() = runTest {
        val adapter = FakeSchoolAdapter()
        useCase(adapter = adapter).invoke(
            ImportRequest(schoolId = adapter.schoolId, payload = SAMPLE_PAYLOAD),
        )
        assertEquals("给了 payload 就不应再抓取", 0, adapter.fetchCount)
    }

    @Test
    fun missingPayloadTriggersAdapterFetch() = runTest {
        val adapter = FakeSchoolAdapter()
        useCase(adapter = adapter).invoke(ImportRequest(schoolId = adapter.schoolId))
        assertEquals(1, adapter.fetchCount)
    }

    @Test
    fun successfulLoginPersistsCredentials() = runTest {
        val adapter = FakeSchoolAdapter()
        val repository = FakeSchoolRepository()
        useCase(adapter = adapter, schoolRepository = repository).invoke(
            ImportRequest(
                schoolId = adapter.schoolId,
                payload = SAMPLE_PAYLOAD,
                credentials = credentials,
            ),
        )
        assertNotNull(repository.storedOnLogin)
        assertEquals("u", repository.storedOnLogin?.username)
    }

    @Test
    fun storedCredentialsAreUsedWhenRequestHasNone() = runTest {
        val adapter = FakeSchoolAdapter(requiresNetwork = true)
        val repository = FakeSchoolRepository(storedCredentials = credentials)
        val result = useCase(adapter = adapter, schoolRepository = repository)
            .invoke(ImportRequest(schoolId = adapter.schoolId, payload = SAMPLE_PAYLOAD))
        assertTrue(result is ImportResult.Success)
    }

    @Test
    fun newImportBecomesActiveTimetable() = runTest {
        val adapter = FakeSchoolAdapter()
        assertNull(settings.current().activeTimetableId)

        val result = useCase(adapter = adapter).invoke(ImportRequest(schoolId = adapter.schoolId))

        val success = result as ImportResult.Success
        assertEquals(success.timetableId, settings.current().activeTimetableId)
    }

    @Test
    fun importTargetsRequestedTimetableAndKeepsActiveUntouched() = runTest {
        val adapter = FakeSchoolAdapter()
        val repository = FakeTimetableRepository()
        val existing = repository.upsertTimetable(timetable("已有课表", listOf(course("旧课"))))
        settings.setActiveTimetable(existing)

        val result = useCase(adapter = adapter, timetableRepository = repository).invoke(
            ImportRequest(schoolId = adapter.schoolId, payload = SAMPLE_PAYLOAD, timetableId = existing),
        )

        val success = result as ImportResult.Success
        assertEquals(existing, success.timetableId)
        assertEquals("活跃课表不应因为定向导入而改变", existing, settings.current().activeTimetableId)
    }

    @Test
    fun importIntoExistingTimetableReplacesPreviousCourses() = runTest {
        val adapter = FakeSchoolAdapter()
        val repository = FakeTimetableRepository()
        val first = useCase(adapter = adapter, timetableRepository = repository)
            .invoke(ImportRequest(schoolId = adapter.schoolId, payload = SAMPLE_PAYLOAD)) as ImportResult.Success
        assertEquals(listOf("数据结构"), courseNames(repository, first.timetableId))

        adapter.parsed = timetable("重新导入", listOf(course("高等数学"), course("大学英语")))
        val second = useCase(adapter = adapter, timetableRepository = repository)
            .invoke(
                ImportRequest(
                    schoolId = adapter.schoolId,
                    payload = SAMPLE_PAYLOAD,
                    timetableId = first.timetableId,
                ),
            ) as ImportResult.Success

        assertEquals("必须落到同一张课表", first.timetableId, second.timetableId)
        assertEquals(1, repository.observeTimetables().first().size)
        assertEquals(
            "整表替换：旧课程必须消失",
            listOf("高等数学", "大学英语"),
            courseNames(repository, second.timetableId),
        )
    }

    private suspend fun courseNames(repository: FakeTimetableRepository, timetableId: Long): List<String> =
        repository.timetable(timetableId)?.courses?.map { it.name }.orEmpty()

    private fun timetable(name: String, courses: List<Course>): Timetable = Timetable(
        tableName = name,
        startDate = LocalDate.of(2026, 9, 7),
        courses = courses,
    )

    private fun course(name: String): Course = Course(
        name = name,
        weekday = 2,
        startSection = 1,
        endSection = 2,
        weeks = listOf(1),
    )
}
