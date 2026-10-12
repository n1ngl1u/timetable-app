package com.example.timetable.data.repository

import androidx.room.withTransaction
import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.data.local.dao.CourseDao
import com.example.timetable.data.local.dao.TimeSlotDao
import com.example.timetable.data.local.dao.TimetableDao
import com.example.timetable.data.local.db.AppDatabase
import com.example.timetable.data.local.entity.TimetableEntity
import com.example.timetable.data.mapper.toDomain
import com.example.timetable.data.mapper.toEntity
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.repository.TimetableRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class TimetableRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val timetableDao: TimetableDao,
    private val courseDao: CourseDao,
    private val timeSlotDao: TimeSlotDao,
    private val dispatchers: DispatcherProvider,
) : TimetableRepository {

    override fun observeTimetables(): Flow<List<Timetable>> =
        timetableDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observeTimetable(timetableId: Long): Flow<Timetable?> =
        timetableDao.observeById(timetableId)
            .map { entity -> entity?.let { loadFull(it) } }
            .flowOn(dispatchers.io)

    override fun observeCourses(timetableId: Long): Flow<List<Course>> =
        courseDao.observeByTimetable(timetableId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override suspend fun timetable(timetableId: Long): Timetable? = withContext(dispatchers.io) {
        timetableDao.findById(timetableId)?.let { loadFull(it) }
    }

    override suspend fun coursesOn(timetableId: Long, weekday: Int, week: Int): List<Course> =
        withContext(dispatchers.io) {
            courseDao.findByWeekday(timetableId, weekday)
                .map { it.toDomain() }
                .filter { it.isActiveInWeek(week) }
                .sortedBy { it.startSection }
        }

    override suspend fun upsertTimetable(timetable: Timetable): Long =
        withContext(dispatchers.io) {
            database.withTransaction {
                val entity = timetable.toEntity()
                val timetableId = if (entity.id == 0L) {
                    timetableDao.insert(entity)
                } else {
                    timetableDao.update(entity)
                    entity.id
                }
                timeSlotDao.deleteByTimetable(timetableId)
                if (timetable.timeSlots.isNotEmpty()) {
                    timeSlotDao.insertAll(timetable.timeSlots.map { it.toEntity(timetableId) })
                }
                courseDao.deleteByTimetable(timetableId)
                if (timetable.courses.isNotEmpty()) {
                    courseDao.insertAll(timetable.courses.map { it.toEntity(timetableId) })
                }
                timetableId
            }
        }

    override suspend fun deleteTimetable(timetableId: Long) {
        withContext(dispatchers.io) {
            timetableDao.deleteById(timetableId)
        }
    }

    private suspend fun loadFull(entity: TimetableEntity): Timetable = entity.toDomain(
        timeSlots = timeSlotDao.findByTimetable(entity.id).map { it.toDomain() },
        courses = courseDao.findByTimetable(entity.id).map { it.toDomain() },
    )
}
