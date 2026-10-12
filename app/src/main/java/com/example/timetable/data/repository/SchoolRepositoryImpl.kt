package com.example.timetable.data.repository

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.data.adapter.json.CourseParseException
import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.ImportResult
import com.example.timetable.domain.importer.LoginState
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolRegistry
import com.example.timetable.domain.importer.SchoolSession
import com.example.timetable.domain.repository.SchoolRepository
import com.example.timetable.domain.repository.TimetableRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class SchoolRepositoryImpl @Inject constructor(
    private val registry: SchoolRegistry,
    private val timetableRepository: TimetableRepository,
    private val dispatchers: DispatcherProvider,
) : SchoolRepository {

    override fun availableSchools(): List<SchoolAdapter> = registry.adapters()

    override suspend fun importTimetable(request: ImportRequest): ImportResult =
        withContext(dispatchers.io) {
            val adapter = registry.adapter(request.schoolId)
                ?: return@withContext ImportResult.InvalidInput(
                    "未注册的学校适配器: " + request.schoolId,
                )
            try {
                val session = resolveSession(adapter, request)
                    ?: return@withContext ImportResult.SessionExpired("登录态已失效，请重新登录")
                val payload = request.payload
                    ?: adapter.fetchTimetable(session, request.fetchRequest)
                val parsed = adapter.parse(payload)
                val timetable = parsed.copy(
                    tableName = request.tableName ?: parsed.tableName,
                    startDate = request.startDate ?: parsed.startDate,
                )
                val timetableId = timetableRepository.upsertTimetable(timetable)
                ImportResult.Success(
                    timetableId = timetableId,
                    courseCount = timetable.courses.size,
                )
            } catch (error: CourseParseException) {
                if (error.field != null) {
                    ImportResult.MissingField(field = error.field, message = error.message.orEmpty())
                } else {
                    ImportResult.SchemaChanged(error.message.orEmpty(), error)
                }
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
        val credentials = request.credentials ?: return SchoolSession(adapter.schoolId)
        return when (val state = adapter.fetchLogin(credentials)) {
            is LoginState.Success -> SchoolSession(adapter.schoolId)
            is LoginState.SessionExpired -> null
            is LoginState.Failed -> null
        }
    }
}
