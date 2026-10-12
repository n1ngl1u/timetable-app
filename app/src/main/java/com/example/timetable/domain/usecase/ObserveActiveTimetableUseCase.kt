package com.example.timetable.domain.usecase

import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 活跃课表读取侧的唯一出口。activeTimetableId 为 null 时一律返回空值，不发起查询、不抛异常。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveActiveTimetableUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val timetableRepository: TimetableRepository,
) {

    val activeTimetableId: Flow<Long?> = settingsRepository.settings
        .map { settings -> settings.activeTimetableId }
        .distinctUntilChanged()

    val activeTimetable: Flow<Timetable?> = activeTimetableId.flatMapLatest { timetableId ->
        if (timetableId == null) {
            flowOf(null)
        } else {
            timetableRepository.observeTimetable(timetableId)
        }
    }

    val activeCourseCount: Flow<Int> = activeTimetableId.flatMapLatest { timetableId ->
        timetableRepository.observeCourseCount(timetableId)
    }

    suspend fun activeTimetableOnce(): Timetable? {
        val timetableId = activeTimetableId.first() ?: return null
        return timetableRepository.timetable(timetableId)
    }
}
