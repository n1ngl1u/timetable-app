package com.example.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.timetable.core.perf.JankStatsTracker
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import com.example.timetable.navigation.AppNavHost
import com.example.timetable.ui.theme.TimetableTheme
import com.example.timetable.widget.WidgetDataObserver
import com.example.timetable.data.reminder.ReminderScheduler
import com.example.timetable.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var perfTracer: PerfTracer

    @Inject
    lateinit var jankStatsTracker: JankStatsTracker

    @Inject
    lateinit var widgetDataObserver: WidgetDataObserver

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        perfTracer.mark(TraceTags.ACTIVITY_ON_CREATE)
        jankStatsTracker.start(this)
        val payload = intent?.getStringExtra(EXTRA_TIMETABLE_JSON)?.takeIf { it.isNotBlank() }
        setContent {
            TimetableTheme {
                // 不声明 configChanges，旋转走默认重建，windowSizeClass 随之更新
                AppNavHost(
                    windowSizeClass = calculateWindowSizeClass(this),
                    autoImportPayload = payload,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 观察窗口限定在前台：避免把一次 SQLCipher 开库塞进冷启动路径
        widgetDataObserver.start(lifecycleScope) { widgetRefresher.refreshAll() }
        rescheduleRemindersOnce()
    }

    override fun onStop() {
        widgetDataObserver.stop()
        super.onStop()
    }

    /**
     * 进程内首次进入前台时重排一次提醒。刻意不放在 Application.onCreate：
     * 重排要读库，会直接压在冷启动 ≤800ms 的预算上。
     */
    private fun rescheduleRemindersOnce() {
        if (remindersRescheduled) return
        remindersRescheduled = true
        lifecycleScope.launch { reminderScheduler.rescheduleAll() }
    }

    override fun onDestroy() {
        jankStatsTracker.stop()
        super.onDestroy()
    }

    private var remindersRescheduled = false

    companion object {
        /** 与 [EXTRA_TIMETABLE_JSON] 同时使用；单独给 JSON 也会触发导入，此开关仅为基准测试语义显式化。 */
        const val EXTRA_AUTO_IMPORT = "auto_import"

        const val EXTRA_TIMETABLE_JSON = "timetable_json"
    }
}
