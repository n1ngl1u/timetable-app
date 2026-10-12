package com.example.timetable.data.repository

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.data.local.dao.ReminderDao
import com.example.timetable.data.local.entity.ReminderEntity
import com.example.timetable.domain.model.ReminderRule
import com.example.timetable.domain.repository.ReminderRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val dispatchers: DispatcherProvider,
) : ReminderRepository {

    override fun observeRules(): Flow<List<ReminderRule>> =
        reminderDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observeRulesForCourse(courseId: Long): Flow<List<ReminderRule>> =
        reminderDao.observeByCourse(courseId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override suspend fun upsertRule(rule: ReminderRule): Long = withContext(dispatchers.io) {
        reminderDao.insert(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: Long) {
        withContext(dispatchers.io) {
            reminderDao.deleteById(ruleId)
        }
    }

    private fun ReminderEntity.toDomain(): ReminderRule = ReminderRule(
        id = id,
        courseId = courseId,
        advanceMinutes = advanceMinutes,
        enabled = enabled,
    )

    private fun ReminderRule.toEntity(): ReminderEntity = ReminderEntity(
        id = id,
        courseId = courseId,
        advanceMinutes = advanceMinutes,
        enabled = enabled,
    )
}
