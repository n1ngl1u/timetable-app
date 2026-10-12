package com.example.timetable.domain.repository

import com.example.timetable.domain.model.ReminderRule
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {

    fun observeRules(): Flow<List<ReminderRule>>

    fun observeRulesForCourse(courseId: Long): Flow<List<ReminderRule>>

    suspend fun upsertRule(rule: ReminderRule): Long

    suspend fun deleteRule(ruleId: Long)
}
