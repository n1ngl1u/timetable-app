package com.example.timetable.data.repository

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.data.crypto.SecurePrefsStore
import com.example.timetable.domain.importer.LoginState
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolCredentials
import com.example.timetable.domain.importer.SchoolRegistry
import com.example.timetable.domain.importer.SchoolSession
import com.example.timetable.domain.importer.TimetableFetchRequest
import com.example.timetable.domain.repository.SchoolRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class SchoolRepositoryImpl @Inject constructor(
    private val registry: SchoolRegistry,
    private val securePrefsStore: SecurePrefsStore,
    private val dispatchers: DispatcherProvider,
) : SchoolRepository {

    override fun availableSchools(): List<SchoolAdapter> = registry.adapters()

    override suspend fun login(
        adapter: SchoolAdapter,
        credentials: SchoolCredentials,
    ): LoginState = withContext(dispatchers.io) { adapter.fetchLogin(credentials) }

    override suspend fun fetchPayload(
        adapter: SchoolAdapter,
        session: SchoolSession,
        request: TimetableFetchRequest,
    ): String = withContext(dispatchers.io) { adapter.fetchTimetable(session, request) }

    override fun readStoredCredentials(schoolId: String): SchoolCredentials? =
        securePrefsStore.readCredentials(schoolId)

    override fun storeCredentials(schoolId: String, credentials: SchoolCredentials) {
        securePrefsStore.putCredentials(schoolId, credentials)
    }
}
