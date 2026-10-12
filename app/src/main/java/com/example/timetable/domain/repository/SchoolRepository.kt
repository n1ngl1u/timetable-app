package com.example.timetable.domain.repository

import com.example.timetable.domain.importer.LoginState
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolCredentials
import com.example.timetable.domain.importer.SchoolSession
import com.example.timetable.domain.importer.TimetableFetchRequest

/**
 * 学校适配器的数据层门面：只负责「目录 / 登录 / 取原始载荷 / 凭据持久化」。
 * 导入编排（registry -> 解析 -> 落库 -> 错误映射）由
 * [com.example.timetable.domain.usecase.ImportTimetableUseCase] 承担。
 */
interface SchoolRepository {

    fun availableSchools(): List<SchoolAdapter>

    suspend fun login(adapter: SchoolAdapter, credentials: SchoolCredentials): LoginState

    suspend fun fetchPayload(
        adapter: SchoolAdapter,
        session: SchoolSession,
        request: TimetableFetchRequest,
    ): String

    fun readStoredCredentials(schoolId: String): SchoolCredentials?

    fun storeCredentials(schoolId: String, credentials: SchoolCredentials)
}
