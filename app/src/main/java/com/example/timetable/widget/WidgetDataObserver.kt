package com.example.timetable.widget

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import com.example.timetable.domain.usecase.ObserveActiveTimetableUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/** 小组件数据变更信号：课表张数 + 当前活跃课表课程数 + 活跃课表 id。 */
data class WidgetRevision(
    val timetableCount: Int,
    val courseCount: Int,
    val activeTimetableId: Long?,
)

/**
 * 数据变更 -> 小组件刷新的触发点。课程数取自活跃课表（多课表下全表计数会串台），
 * 因此切换活跃课表会改变 [revisions]，从而自动触发刷新。
 *
 * 导入时的批量 upsert 会连发多条失效通知，这里用 conflate 把突发合并为一次尾随刷新
 * （不用 debounce：静默窗口会直接吃掉 500ms 预算）。
 *
 * 本类不含任何 Android 依赖，[revisions] 可被单测直接订阅；刷新动作由调用方注入。
 */
@Singleton
class WidgetDataObserver @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val settingsRepository: SettingsRepository,
    private val observeActiveTimetable: ObserveActiveTimetableUseCase,
    private val dispatchers: DispatcherProvider,
) {

    val revisions: Flow<WidgetRevision> = combine(
        timetableRepository.observeTimetables(),
        observeActiveTimetable.activeCourseCount,
        settingsRepository.settings,
    ) { timetables, courseCount, settings ->
        WidgetRevision(
            timetableCount = timetables.size,
            courseCount = courseCount,
            activeTimetableId = settings.activeTimetableId,
        )
    }.conflate()

    private var observeJob: Job? = null

    fun start(scope: CoroutineScope, onRevision: suspend (WidgetRevision) -> Unit) {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch(dispatchers.default) {
            revisions.collect { revision -> onRevision(revision) }
        }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
    }
}
