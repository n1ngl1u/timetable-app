package com.example.timetable.testing

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.core.time.TimeProvider
import com.example.timetable.domain.importer.LoginState
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolCredentials
import com.example.timetable.domain.importer.SchoolRegistry
import com.example.timetable.domain.importer.SchoolSession
import com.example.timetable.domain.importer.TimetableFetchRequest
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.repository.SchoolRepository
import com.example.timetable.domain.repository.TimetableRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeTimeProvider(
    private var today: LocalDate = LocalDate.of(2026, 9, 7),
    private var elapsed: Long = 0L,
) : TimeProvider {

    override fun epochMillis(): Long = elapsed

    override fun elapsedRealtimeMillis(): Long = elapsed

    override fun today(): LocalDate = today

    fun advance(millis: Long) {
        elapsed += millis
    }
}

class FakeDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
}

class FakeSchoolAdapter(
    override val schoolId: String = SCHOOL_ID,
    override val displayName: String = "FakeSchool",
    override val requiresNetwork: Boolean = false,
    private val loginState: LoginState = LoginState.Success,
    private val payload: String = SAMPLE_PAYLOAD,
    private val fetchError: Throwable? = null,
    private val parseError: Throwable? = null,
    private val parsed: Timetable = SAMPLE_TIMETABLE,
) : SchoolAdapter {

    var fetchCount: Int = 0
        private set

    override suspend fun fetchLogin(credentials: SchoolCredentials): LoginState = loginState

    override suspend fun fetchTimetable(
        session: SchoolSession,
        request: TimetableFetchRequest,
    ): String {
        fetchCount++
        fetchError?.let { throw it }
        return payload
    }

    override fun parse(payload: String): Timetable {
        parseError?.let { throw it }
        return parsed
    }

    companion object {
        const val SCHOOL_ID = "fake-school"
    }
}

class FakeSchoolRegistry(
    adapters: List<SchoolAdapter> = emptyList(),
) : SchoolRegistry {

    private val byId = LinkedHashMap<String, SchoolAdapter>()

    init {
        adapters.forEach { byId[it.schoolId] = it }
    }

    override fun register(adapter: SchoolAdapter) {
        byId[adapter.schoolId] = adapter
    }

    override fun adapter(schoolId: String): SchoolAdapter? = byId[schoolId]

    override fun adapters(): List<SchoolAdapter> = byId.values.toList()
}

class FakeSchoolRepository(
    private val adapters: List<SchoolAdapter> = emptyList(),
    private val loginState: LoginState = LoginState.Success,
    private val storedCredentials: SchoolCredentials? = null,
    private val fetchError: Throwable? = null,
) : SchoolRepository {

    var storedOnLogin: SchoolCredentials? = null
        private set

    override fun availableSchools(): List<SchoolAdapter> = adapters

    override suspend fun login(
        adapter: SchoolAdapter,
        credentials: SchoolCredentials,
    ): LoginState = loginState

    override suspend fun fetchPayload(
        adapter: SchoolAdapter,
        session: SchoolSession,
        request: TimetableFetchRequest,
    ): String {
        fetchError?.let { throw it }
        return adapter.fetchTimetable(session, request)
    }

    override fun readStoredCredentials(schoolId: String): SchoolCredentials? = storedCredentials

    override fun storeCredentials(schoolId: String, credentials: SchoolCredentials) {
        storedOnLogin = credentials
    }
}

class FakeTimetableRepository(
    private val upsertError: Throwable? = null,
    private val nextId: Long = 1L,
) : TimetableRepository {

    private val timetables = MutableStateFlow<List<Timetable>>(emptyList())

    var upserted: Timetable? = null
        private set

    override fun observeTimetables(): Flow<List<Timetable>> = timetables.asStateFlow()

    override fun observeTimetable(timetableId: Long): Flow<Timetable?> =
        timetables.map { list -> list.firstOrNull { it.id == timetableId } }

    override fun observeCourses(timetableId: Long): Flow<List<Course>> =
        timetables.map { list -> list.firstOrNull { it.id == timetableId }?.courses.orEmpty() }

    override fun observeTotalCourseCount(): Flow<Int> =
        timetables.map { list -> list.sumOf { it.courses.size } }

    override suspend fun timetable(timetableId: Long): Timetable? =
        timetables.value.firstOrNull { it.id == timetableId }

    override suspend fun coursesOn(timetableId: Long, weekday: Int, week: Int): List<Course> =
        timetables.value.firstOrNull { it.id == timetableId }
            ?.courses
            ?.filter { it.weekday == weekday && it.isActiveInWeek(week) }
            .orEmpty()

    override suspend fun upsertTimetable(timetable: Timetable): Long {
        upsertError?.let { throw it }
        val stored = timetable.copy(id = if (timetable.id == 0L) nextId else timetable.id)
        upserted = stored
        timetables.value = listOf(stored)
        return stored.id
    }

    override suspend fun deleteTimetable(timetableId: Long) {
        timetables.value = emptyList()
    }
}

val SAMPLE_PAYLOAD: String = """
    {
      "tableName": "2026 秋 计算机",
      "startDate": "2026-09-07",
      "timeSlots": [
        { "number": 1, "startTime": "08:00", "endTime": "08:45" },
        { "number": 2, "startTime": "08:55", "endTime": "09:40" }
      ],
      "courses": [
        {
          "name": "数据结构",
          "teacher": "王强",
          "location": "计算机楼 402",
          "weekday": 2,
          "startSection": 1,
          "endSection": 2,
          "weeks": [1, 2, 3]
        }
      ]
    }
""".trimIndent()

val SAMPLE_TIMETABLE: Timetable = Timetable(
    tableName = "2026 秋 计算机",
    startDate = LocalDate.of(2026, 9, 7),
    timeSlots = listOf(
        TimeSlot(number = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlot(number = 2, startTime = "08:55", endTime = "09:40"),
    ),
    courses = listOf(
        Course(
            name = "数据结构",
            teacher = "王强",
            location = "计算机楼 402",
            weekday = 2,
            startSection = 1,
            endSection = 2,
            weeks = listOf(1, 2, 3),
        ),
    ),
)
