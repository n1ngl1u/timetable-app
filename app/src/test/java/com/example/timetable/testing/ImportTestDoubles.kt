package com.example.timetable.testing

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.core.time.TimeProvider
import com.example.timetable.domain.importer.LoginState
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolCredentials
import com.example.timetable.domain.importer.SchoolRegistry
import com.example.timetable.domain.importer.SchoolSession
import com.example.timetable.domain.importer.TimetableFetchRequest
import com.example.timetable.domain.calendar.CalendarEventLink
import com.example.timetable.domain.calendar.CalendarSyncOutcome
import com.example.timetable.domain.calendar.CalendarSyncPlan
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.domain.model.AppSettings
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.ReminderRule
import com.example.timetable.domain.model.ReminderSchedule
import com.example.timetable.domain.model.Semester
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.repository.SchoolRepository
import com.example.timetable.domain.repository.CalendarRepository
import com.example.timetable.domain.repository.ReminderRepository
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
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
    var parsed: Timetable = SAMPLE_TIMETABLE,
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

    private val semesters = MutableStateFlow<List<Semester>>(emptyList())

    var upserted: Timetable? = null
        private set

    override fun observeTimetables(): Flow<List<Timetable>> = timetables.asStateFlow()

    override fun observeTimetable(timetableId: Long): Flow<Timetable?> =
        timetables.map { list -> list.firstOrNull { it.id == timetableId } }

    override fun observeCourses(timetableId: Long): Flow<List<Course>> =
        timetables.map { list -> list.firstOrNull { it.id == timetableId }?.courses.orEmpty() }

    override fun observeCourseCount(timetableId: Long?): Flow<Int> =
        if (timetableId == null) {
            flowOf(0)
        } else {
            timetables.map { list ->
                list.firstOrNull { it.id == timetableId }?.courses?.size ?: 0
            }
        }

    override fun observeSemesters(): Flow<List<Semester>> = semesters.asStateFlow()

    override suspend fun timetable(timetableId: Long): Timetable? =
        timetables.value.firstOrNull { it.id == timetableId }

    override suspend fun coursesOn(timetableId: Long, weekday: Int, week: Int): List<Course> =
        timetables.value.firstOrNull { it.id == timetableId }
            ?.courses
            ?.filter { it.weekday == weekday && it.isActiveInWeek(week) }
            .orEmpty()

    /** 与 TimetableRepositoryImpl 一致：按 id 合并，同 id 视为整表替换。 */
    override suspend fun upsertTimetable(timetable: Timetable): Long {
        upsertError?.let { throw it }
        val current = timetables.value
        val stored = timetable.copy(id = if (timetable.id == 0L) nextId + current.size else timetable.id)
        upserted = stored
        timetables.value = current.filterNot { it.id == stored.id } + stored
        return stored.id
    }

    override suspend fun deleteTimetable(timetableId: Long) {
        timetables.value = timetables.value.filterNot { it.id == timetableId }
    }

    val deletedCourseIds = mutableListOf<Long>()

    override suspend fun deleteCourse(courseId: Long) {
        deletedCourseIds += courseId
        timetables.value = timetables.value.map { timetable ->
            timetable.copy(courses = timetable.courses.filterNot { it.id == courseId })
        }
    }

    // 学期方法完整实现（非 UnsupportedOperationException）；本批无单测覆盖，学期模块接入后补。
    override suspend fun upsertSemester(semester: Semester): Long {
        val current = semesters.value
        val stored = semester.copy(id = if (semester.id == 0L) nextId + current.size else semester.id)
        semesters.value = current.filterNot { it.id == stored.id } + stored
        return stored.id
    }

    override suspend fun setActiveSemester(semesterId: Long) {
        semesters.value = semesters.value.map { it.copy(isActive = it.id == semesterId) }
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state.asStateFlow()

    override suspend fun current(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }

    override suspend fun setPureModeEnabled(enabled: Boolean) {
        state.value = state.value.copy(pureModeEnabled = enabled)
    }

    override suspend fun setActiveTimetable(timetableId: Long?) {
        state.value = state.value.copy(activeTimetableId = timetableId)
    }
}

/** 构造一张带 N 门课的课表，供多课表切换类测试使用。 */
fun populatedTimetable(
    name: String,
    courseCount: Int,
    startDate: LocalDate = LocalDate.of(2026, 9, 7),
): Timetable = Timetable(
    tableName = name,
    startDate = startDate,
    timeSlots = listOf(TimeSlot(number = 1, startTime = "08:00", endTime = "08:45")),
    courses = (1..courseCount).map { index ->
        Course(
            id = index.toLong(),
            name = name + " 课程 " + index,
            weekday = 1,
            startSection = 1,
            endSection = 1,
            weeks = listOf(1),
        )
    },
)

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

class FakeReminderRepository : ReminderRepository {

    private val rules = MutableStateFlow<List<ReminderRule>>(emptyList())

    private var nextId = 1L

    override fun observeRules(): Flow<List<ReminderRule>> = rules.asStateFlow()

    override fun observeRulesForCourse(courseId: Long): Flow<List<ReminderRule>> =
        rules.map { list -> list.filter { it.courseId == courseId } }

    override suspend fun ruleForCourse(courseId: Long): ReminderRule? =
        rules.value.firstOrNull { it.courseId == courseId }

    /**
     * 调度投影（reminders ⋈ courses ⋈ timetables ⟕ time_slots）是 Room JOIN 的真实语义，
     * 本双件不持有课程/课表数据，无法如实重建；该路径由 ReminderScheduler 在设备侧验证。
     */
    override suspend fun enabledSchedules(): List<ReminderSchedule> = emptyList()

    override suspend fun scheduleFor(ruleId: Long): ReminderSchedule? = null

    override suspend fun upsertRule(rule: ReminderRule): Long {
        val stored = if (rule.id == 0L) rule.copy(id = nextId++) else rule
        rules.value = rules.value.filterNot { it.id == stored.id } + stored
        return stored.id
    }

    override suspend fun deleteRule(ruleId: Long) {
        rules.value = rules.value.filterNot { it.id == ruleId }
    }
}

/**
 * 日历仓储替身。
 *
 * 局限（已登记进 docs/device-verification.md）：本类**不模拟 ContentResolver**，
 * 不碰 CalendarContract，也不产生真实的日历事件；applyChanges 只记录被调用的计划。
 * 系统日历的真实行为（建日历、插入、RRULE 展开、权限路径）只能靠真机覆盖。
 */
class FakeCalendarRepository(
    private val permissionsGranted: Boolean = true,
    private val calendarId: Long? = 1L,
    private val deleteResult: CalendarSyncResult? = null,
) : CalendarRepository {

    val links = mutableListOf<CalendarEventLink>()

    var appliedPlans = mutableListOf<CalendarSyncPlan>()

    var pruneCallCount = 0
        private set

    var clearCallCount = 0
        private set

    val deletedCourseIds = mutableListOf<Long>()

    override suspend fun hasCalendarPermissions(): Boolean = permissionsGranted

    override suspend fun ensureCourseCalendar(): Long? =
        if (permissionsGranted) calendarId else null

    override suspend fun linksForCourses(courseIds: List<Long>): List<CalendarEventLink> =
        links.filter { courseIds.contains(it.courseId) }

    override suspend fun applyChanges(plan: CalendarSyncPlan): CalendarSyncResult {
        if (!permissionsGranted) {
            return CalendarSyncResult.Skipped(
                com.example.timetable.domain.calendar.CalendarSkipReason.PERMISSION_DENIED,
            )
        }
        appliedPlans += plan
        return CalendarSyncResult.Synced(
            CalendarSyncOutcome(
                inserted = plan.inserts.size,
                updated = plan.updates.size,
                deleted = plan.deletes.size,
                unchanged = plan.unchanged.size,
                skippedCourses = plan.skippedCourses,
            ),
        )
    }

    override suspend fun deleteEventsForCourse(courseId: Long): CalendarSyncResult {
        deletedCourseIds += courseId
        return deleteResult ?: CalendarSyncResult.Synced(CalendarSyncOutcome())
    }

    override suspend fun pruneOrphanEvents(): Int {
        pruneCallCount++
        return 0
    }

    override suspend fun clearSyncedEvents(): CalendarSyncResult {
        clearCallCount++
        links.clear()
        return CalendarSyncResult.Synced(CalendarSyncOutcome())
    }
}
