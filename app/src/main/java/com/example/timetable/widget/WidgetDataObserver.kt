package com.example.timetable.widget

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * 数据变更 -> 小组件刷新的触发点。导入时的批量 upsert 会连发多条失效通知，
 * 这里用 conflate 把突发合并为一次尾随刷新（不用 debounce：静默窗口会直接吃掉 500ms 预算），
 * 再由 [WidgetRefresher] 的 Mutex 保证真正串行。
 */
@Singleton
class WidgetDataObserver @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val settingsRepository: SettingsRepository,
    private val widgetRefresher: WidgetRefresher,
    private val dispatchers: DispatcherProvider,
) {

    private var observeJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch(dispatchers.default) {
            combine(
                timetableRepository.observeTimetables(),
                timetableRepository.observeTotalCourseCount(),
                settingsRepository.settings,
            ) { timetables, courseCount, settings ->
                Revision(
                    timetableCount = timetables.size,
                    courseCount = courseCount,
                    activeTimetableId = settings.activeTimetableId,
                )
            }
                .conflate()
                .collect { widgetRefresher.refreshAll() }
        }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
    }

    private data class Revision(
        val timetableCount: Int,
        val courseCount: Int,
        val activeTimetableId: Long,
    )
}
