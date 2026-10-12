package com.example.timetable

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.timetable.core.perf.JankStatsTracker
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import com.example.timetable.navigation.AppNavHost
import com.example.timetable.ui.puremode.PureModeViewModel
import com.example.timetable.ui.theme.ThemeViewModel
import com.example.timetable.ui.theme.TimetableThemeHost
import com.example.timetable.ui.theme.TimetableTheme
import com.example.timetable.widget.WidgetDataObserver
import com.example.timetable.data.reminder.ReminderScheduler
import com.example.timetable.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    private val pureModeViewModel: PureModeViewModel by viewModels()


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
        observePureMode()
        setContent {
            // 主题偏好变化只重组，不重建 Activity
            TimetableThemeHost(viewModel = themeViewModel) {
                // 不声明 configChanges，旋转走默认重建，windowSizeClass 随之更新
                AppNavHost(
                    windowSizeClass = calculateWindowSizeClass(this),
                    autoImportPayload = payload,
                    pureMode = pureModeViewModel,
                )
            }
        }
    }

    /**
     * 开关是唯一真源：STARTED 起收集，重建后会立刻收到当前值并重新应用，
     * 因此进程重建后纯净模式仍然生效，不需要在 onCreate 里另存一份状态。
     * 响应是双向的：进入隐藏系统栏，退出恢复系统栏。
     */
    private fun observePureMode() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    pureModeViewModel.enabled.collect { enabled ->
                        applySystemBarsVisible(!enabled)
                    }
                }
                launch {
                    pureModeViewModel.messages.collect { message ->
                        Toast.makeText(this@MainActivity, message.textRes, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun applySystemBarsVisible(visible: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
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
