package com.example.timetable.domain.usecase

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.domain.importer.ImportIoException
import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.ImportResult
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolRegistry
import com.example.timetable.domain.importer.SchoolSession
import com.example.timetable.domain.repository.SchoolRepository
import com.example.timetable.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** 载荷准备结果。 */
sealed interface ImportPreparation {

    /**
     * 载荷已就绪且能解析出 [newCourseCount] 门课程。
     * 是否需要二次确认由调用方决定 —— 它才知道当前有没有活跃课表。
     */
    data class Prepared(
        val request: ImportRequest,
        val newCourseCount: Int,
    ) : ImportPreparation

    /**
     * 载荷已就绪但解析失败。**这里刻意不拦**：真正的错误映射在
     * [ImportTimetableUseCase] 里（缺字段 / 结构变化 / 输入非法），
     * 在这里重复一份映射只会让两处慢慢漂移。直接放行，让导入链路给出准确原因。
     */
    data class Unparsable(val request: ImportRequest) : ImportPreparation

    /** 连载荷都没拿到：文件读不了、剪贴板为空、适配器未注册。 */
    data class Failure(val message: String) : ImportPreparation
}

sealed interface ImportOutcome {

    data class Success(
        val timetableId: Long,
        val courseCount: Int,
        /**
         * 日历同步结果，三层语义必须分清：
         * - null —— 日历同步开关**未开启**，根本没调用同步；
         * - [CalendarSyncResult.Skipped] —— 开关开着，但同步被跳过（无活跃课表 / 无权限 / 无课程）；
         * - 其它 —— 已经尝试过同步（成功或失败都在这层，失败时由调用方追加提示）。
         */
        val calendarSync: CalendarSyncResult?,
    ) : ImportOutcome

    data class Failure(val message: String) : ImportOutcome
}

/**
 * 导入入口的唯一编排点：准备载荷 -> 交给 [ImportTimetableUseCase] 落库 -> 按开关决定是否同步日历。
 *
 * 本类**不修改** [ImportTimetableUseCase] 的编排：preview 用的解析与最终的落库解析是两次调用，
 * 载荷在准备阶段就已经取到并塞进 [ImportRequest.payload]，落库时不会再读一次文件。
 *
 * 只服务不触网的本地适配器：真正的网络型适配器接入时必须补齐与
 * [ImportTimetableUseCase] 一致的登录流程，这里直接拒绝而不是假装能跑。
 */
class ImportAndSyncUseCase @Inject constructor(
    private val importTimetable: ImportTimetableUseCase,
    private val syncToCalendar: SyncToCalendarUseCase,
    private val schoolRegistry: SchoolRegistry,
    private val schoolRepository: SchoolRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun prepare(request: ImportRequest): ImportPreparation = withContext(dispatchers.io) {
        val adapter = schoolRegistry.adapter(request.schoolId)
            ?: return@withContext ImportPreparation.Failure(
                "未注册的学校适配器: " + request.schoolId,
            )
        if (adapter.requiresNetwork) {
            return@withContext ImportPreparation.Failure("该导入入口只支持本地文件或文本")
        }
        val payload = try {
            resolvePayload(adapter, request)
        } catch (error: ImportIoException) {
            return@withContext ImportPreparation.Failure(error.message ?: "课表文件读取失败")
        } catch (error: Exception) {
            return@withContext ImportPreparation.Failure(error.message ?: "课表内容读取失败")
        }
        val ready = request.copy(payload = payload)
        val parsed = runCatching { adapter.parse(payload) }.getOrNull()
            ?: return@withContext ImportPreparation.Unparsable(ready)
        ImportPreparation.Prepared(request = ready, newCourseCount = parsed.courses.size)
    }

    /**
     * @param syncCalendar 为 false 时**完全不碰**设置与日历同步，返回的 calendarSync 恒为 null。
     *   基准测试链路必须传 false：否则 IMPORT_FIRST_FRAME 的 end 会落在同步之后，
     *   指标会被一段与导入无关的耗时污染。
     */
    suspend fun commit(
        request: ImportRequest,
        syncCalendar: Boolean = true,
    ): ImportOutcome = withContext(dispatchers.io) {
        when (val result = importTimetable(request)) {
            is ImportResult.Success -> {
                val syncEnabled = syncCalendar && settingsRepository.current().calendarSyncEnabled
                val sync = if (syncEnabled) syncToCalendar() else null
                ImportOutcome.Success(
                    timetableId = result.timetableId,
                    courseCount = result.courseCount,
                    calendarSync = sync,
                )
            }
            is ImportResult.NetworkFailure -> ImportOutcome.Failure(result.message)
            is ImportResult.SessionExpired -> ImportOutcome.Failure(result.message)
            is ImportResult.SchemaChanged -> ImportOutcome.Failure(result.message)
            is ImportResult.MissingField -> ImportOutcome.Failure(result.message)
            is ImportResult.InvalidInput -> ImportOutcome.Failure(result.message)
        }
    }

    private suspend fun resolvePayload(adapter: SchoolAdapter, request: ImportRequest): String {
        request.payload?.takeIf { it.isNotBlank() }?.let { return it }
        val fetched = schoolRepository.fetchPayload(
            adapter = adapter,
            session = SchoolSession(schoolId = adapter.schoolId),
            request = request.fetchRequest,
        )
        if (fetched.isBlank()) {
            throw ImportIoException("课表内容为空")
        }
        return fetched
    }
}
