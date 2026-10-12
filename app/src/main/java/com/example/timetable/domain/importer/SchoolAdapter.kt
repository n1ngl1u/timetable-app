package com.example.timetable.domain.importer

import com.example.timetable.domain.model.Timetable
import java.time.LocalDate

/**
 * 教务账号凭据，仅经 [com.example.timetable.data.crypto.SecurePrefsStore] 加密落盘。
 */
data class SchoolCredentials(
    val username: String,
    val password: String,
)

/**
 * 登录会话：网络型适配器用它承载 Cookie / Token，本地适配器为空会话。
 */
data class SchoolSession(
    val schoolId: String,
    val cookies: Map<String, String> = emptyMap(),
    val token: String? = null,
)

/**
 * 取课表入参。[sourceUri] 供本地文件型适配器读取 content:// 数据。
 */
data class TimetableFetchRequest(
    val semesterName: String? = null,
    val weeks: List<Int> = emptyList(),
    val sourceUri: String? = null,
)

sealed interface LoginState {
    data object Success : LoginState
    data object SessionExpired : LoginState
    data class Failed(val message: String) : LoginState
}

/**
 * 导入请求。[timetableId] 为空时落到当前活跃课表；活跃课表也为空则新建并置为活跃。
 */
data class ImportRequest(
    val schoolId: String,
    val payload: String? = null,
    val credentials: SchoolCredentials? = null,
    val fetchRequest: TimetableFetchRequest = TimetableFetchRequest(),
    val timetableId: Long? = null,
    val tableName: String? = null,
    val startDate: LocalDate? = null,
)

/**
 * 学校适配器统一契约：新增学校只新增实现 + 注册表条目。
 */
interface SchoolAdapter {

    val schoolId: String

    val displayName: String

    val requiresNetwork: Boolean

    suspend fun fetchLogin(credentials: SchoolCredentials): LoginState

    suspend fun fetchTimetable(session: SchoolSession, request: TimetableFetchRequest): String

    fun parse(payload: String): Timetable
}
