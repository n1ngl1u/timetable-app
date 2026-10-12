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
import com.example.timetable.domain.model.Semester
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.repository.TimetableRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    override fun observeCourseCount(timetableId: Long?): Flow<Int> =
        if (timetableId == null) {
            flowOf(0)
        } else {
            courseDao.observeCountByTimetable(timetableId).flowOn(dispatchers.io)
        }

    override fun observeSemesters(): Flow<List<Semester>> =
        timetableDao.observeSemesters()
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
                // 整表替换：同一事务内先清空旧数据，导入到已有课表时不保留旧课程
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

    override suspend fun upsertSemester(semester: Semester): Long = withContext(dispatchers.io) {
        timetableDao.insertSemester(semester.toEntity())
    }

    override suspend fun setActiveSemester(semesterId: Long) {
        withContext(dispatchers.io) {
            timetableDao.setActiveSemester(semesterId)
        }
    }

    private suspend fun loadFull(entity: TimetableEntity): Timetable = entity.toDomain(
        timeSlots = timeSlotDao.findByTimetable(entity.id).map { it.toDomain() },
        courses = courseDao.findByTimetable(entity.id).map { it.toDomain() },
    )
}
