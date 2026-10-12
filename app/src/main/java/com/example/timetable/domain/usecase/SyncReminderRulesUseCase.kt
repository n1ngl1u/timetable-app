package com.example.timetable.domain.usecase

import com.example.timetable.domain.model.ReminderRule
import com.example.timetable.domain.repository.ReminderRepository
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import javax.inject.Inject

/**
 * 把「全局提醒开关 + 默认提前量」落到当前活跃课表的每门课上。
 * 已存在的规则只更新提前量与开关，不重建，因此重复应用不会产生重复规则；
 * 课程被删除时其规则由外键 CASCADE 一并清理。
 */
class SyncReminderRulesUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val timetableRepository: TimetableRepository,
    private val settingsRepository: SettingsRepository,
) {

    /** 返回本次生效（enabled）的规则数。 */
    suspend fun applyToActiveTimetable(): Int {
        val settings = settingsRepository.current()
        val timetableId = settings.activeTimetableId ?: return 0
        val timetable = timetableRepository.timetable(timetableId) ?: return 0
        var enabledCount = 0
        for (course in timetable.courses) {
            val existing = reminderRepository.ruleForCourse(course.id)
            val rule = ReminderRule(
                id = existing?.id ?: 0L,
                courseId = course.id,
                advanceMinutes = settings.defaultReminderAdvanceMinutes,
                enabled = settings.remindersEnabled,
                weeks = existing?.weeks ?: emptyList(),
            )
            reminderRepository.upsertRule(rule)
            if (rule.enabled) enabledCount++
        }
        return enabledCount
    }
}
