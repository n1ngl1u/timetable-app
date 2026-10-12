package com.example.timetable.domain.usecase

import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 活跃课表写入侧：维护「activeTimetableId 要么指向存在的课表、要么为 null」这条不变式。
 * 本批只交付数据层能力，删除的 UI 入口留到学期管理模块。
 */
class TimetableSelectionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val timetableRepository: TimetableRepository,
) {

    /** 目标课表不存在时保持原值并返回 false。 */
    suspend fun select(timetableId: Long): Boolean {
        if (timetableRepository.timetable(timetableId) == null) return false
        settingsRepository.setActiveTimetable(timetableId)
        return true
    }

    /**
     * 删除课表并转移活跃指向：删的是活跃课表时切到剩余第一张，无剩余则置 null。
     * 返回删除后的活跃课表 id。
     */
    suspend fun delete(timetableId: Long): Long? {
        val wasActive = settingsRepository.current().activeTimetableId == timetableId
        timetableRepository.deleteTimetable(timetableId)
        if (!wasActive) return settingsRepository.current().activeTimetableId
        val nextActiveId = timetableRepository.observeTimetables().first().firstOrNull()?.id
        settingsRepository.setActiveTimetable(nextActiveId)
        return nextActiveId
    }
}
