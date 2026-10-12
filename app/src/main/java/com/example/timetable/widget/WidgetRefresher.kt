package com.example.timetable.widget

import android.content.Context
import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import androidx.glance.appwidget.updateAll
import com.example.timetable.widget.today.TodayCoursesWidget
import com.example.timetable.widget.week.WeekOverviewWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 刷新链路唯一执行体。Mutex 保证串行，两个 widget 的 updateAll 并发执行，
 * T1 取两者都返回的时刻；返回值为 widget_refresh span 的耗时。
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val perfTracer: PerfTracer,
    private val dispatchers: DispatcherProvider,
) {

    private val todayWidget = TodayCoursesWidget()

    private val weekWidget = WeekOverviewWidget()

    private val refreshMutex = Mutex()

    suspend fun refreshAll(): Long = refreshMutex.withLock {
        withContext(dispatchers.default) {
            val startedAt = perfTracer.mark(TraceTags.WIDGET_DATA_CHANGED)
            coroutineScope {
                launch { todayWidget.updateAll(context) }
                launch { weekWidget.updateAll(context) }
            }
            perfTracer.span(TraceTags.WIDGET_REFRESH, startedAt).durationMillis
        }
    }
}
