package com.example.timetable.domain.usecase

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import com.example.timetable.data.adapter.json.CourseParseException
import com.example.timetable.domain.importer.ImportIoException
import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.ImportResult
import com.example.timetable.domain.importer.LoginState
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolRegistry
import com.example.timetable.domain.importer.SchoolSession
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.repository.SchoolRepository
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * 导入编排：registry 取适配器 -> 可选登录 -> 取载荷 -> parse -> 落库，
 * 全程在 IO 线程，并把解析与落库拆成独立 span 供 ≤2s 指标定位。
 *
 * 落库目标：request.timetableId -> 否则当前活跃课表 -> 否则新建；新建成功后置为活跃，
 * 否则 UI 与小组件查不到任何东西。解析逻辑与错误映射不因多课表而改变。
 */
class ImportTimetableUseCase @Inject constructor(
    private val schoolRegistry: SchoolRegistry,
    private val schoolRepository: SchoolRepository,
    private val timetableRepository: TimetableRepository,
    private val settingsRepository: SettingsRepository,
    private val perfTracer: PerfTracer,
    private val dispatchers: DispatcherProvider,
) {

    suspend operator fun invoke(request: ImportRequest): ImportResult = withContext(dispatchers.io) {
        perfTracer.mark(TraceTags.IMPORT_START)
        try {
            val adapter = schoolRegistry.adapter(request.schoolId)
                ?: return@withContext ImportResult.InvalidInput(
                    "未注册的学校适配器: " + request.schoolId,
                )
            val session = resolveSession(adapter, request)
                ?: return@withContext ImportResult.SessionExpired("登录态已失效，请重新登录")
            val payload = request.payload?.takeIf { it.isNotBlank() }
                ?: schoolRepository.fetchPayload(adapter, session, request.fetchRequest)
            val parsed = parse(adapter, payload)
            val requestedId = request.timetableId
            val targetId = requestedId ?: settingsRepository.current().activeTimetableId
            val timetable = parsed.copy(
                id = targetId ?: 0L,
                tableName = request.tableName ?: parsed.tableName,
                startDate = request.startDate ?: parsed.startDate,
            )
            val timetableId = persist(timetable)
            if (targetId == null) {
                settingsRepository.setActiveTimetable(timetableId)
            }
            ImportResult.Success(
                timetableId = timetableId,
                courseCount = timetable.courses.size,
            )
        } catch (error: CourseParseException) {
            mapParseError(error)
        } catch (error: ImportIoException) {
            ImportResult.InvalidInput(error.message ?: "本地课表读取失败", error)
        } catch (error: IOException) {
            ImportResult.NetworkFailure(error.message ?: "网络异常", error)
        } catch (error: Exception) {
            ImportResult.InvalidInput(error.message ?: "导入失败", error)
        }
    }

    private suspend fun resolveSession(
        adapter: SchoolAdapter,
        request: ImportRequest,
    ): SchoolSession? {
        val credentials = request.credentials
            ?: schoolRepository.readStoredCredentials(adapter.schoolId)
        if (credentials == null) {
            return if (adapter.requiresNetwork) null else SchoolSession(adapter.schoolId)
        }
        return when (schoolRepository.login(adapter, credentials)) {
            is LoginState.Success -> {
                // 凭据缓存属尽力而为，失败不影响本次导入结果
                runCatching { schoolRepository.storeCredentials(adapter.schoolId, credentials) }
                SchoolSession(adapter.schoolId)
            }
            is LoginState.SessionExpired -> null
            is LoginState.Failed -> null
        }
    }

    private fun parse(adapter: SchoolAdapter, payload: String): Timetable {
        val startedAt = perfTracer.mark(TraceTags.IMPORT_PARSE)
        return try {
            adapter.parse(payload)
        } finally {
            perfTracer.span(TraceTags.IMPORT_PARSE, startedAt)
        }
    }

    private suspend fun persist(timetable: Timetable): Long {
        val startedAt = perfTracer.mark(TraceTags.IMPORT_PERSIST)
        return try {
            timetableRepository.upsertTimetable(timetable)
        } finally {
            perfTracer.span(TraceTags.IMPORT_PERSIST, startedAt)
        }
    }

    private fun mapParseError(error: CourseParseException): ImportResult {
        val field = error.field
        return if (field != null) {
            ImportResult.MissingField(field = field, message = error.message.orEmpty())
        } else {
            ImportResult.SchemaChanged(error.message.orEmpty(), error)
        }
    }
}
