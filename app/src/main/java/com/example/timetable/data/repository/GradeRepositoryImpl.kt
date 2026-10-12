package com.example.timetable.data.repository

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.data.local.dao.GradeDao
import com.example.timetable.data.mapper.toDomain
import com.example.timetable.data.mapper.toEntity
import com.example.timetable.domain.model.GradeRecord
import com.example.timetable.domain.repository.GradeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class GradeRepositoryImpl @Inject constructor(
    private val gradeDao: GradeDao,
    private val dispatchers: DispatcherProvider,
) : GradeRepository {

    override fun observeGrades(semesterId: Long): Flow<List<GradeRecord>> =
        gradeDao.observeBySemester(semesterId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override suspend fun upsertAll(records: List<GradeRecord>) {
        withContext(dispatchers.io) {
            gradeDao.insertAll(records.map { it.toEntity() })
        }
    }

    override suspend fun deleteBySemester(semesterId: Long) {
        withContext(dispatchers.io) {
            gradeDao.deleteBySemester(semesterId)
        }
    }
}
