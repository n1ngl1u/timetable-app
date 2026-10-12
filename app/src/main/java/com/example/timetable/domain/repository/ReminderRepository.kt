package com.example.timetable.domain.repository

import com.example.timetable.domain.model.ReminderRule
import com.example.timetable.domain.model.ReminderSchedule
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {

    fun observeRules(): Flow<List<ReminderRule>>

    fun observeRulesForCourse(courseId: Long): Flow<List<ReminderRule>>

    suspend fun ruleForCourse(courseId: Long): ReminderRule?

    /** 所有已启用规则连同课程/课表/节次时间的扁平投影，供一次性重排使用。 */
    suspend fun enabledSchedules(): List<ReminderSchedule>

    suspend fun scheduleFor(ruleId: Long): ReminderSchedule?

    suspend fun upsertRule(rule: ReminderRule): Long

    suspend fun deleteRule(ruleId: Long)
}
