package com.example.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.timetable.core.perf.JankStatsTracker
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import com.example.timetable.navigation.AppNavHost
import com.example.timetable.ui.theme.TimetableTheme
import com.example.timetable.widget.WidgetDataObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var perfTracer: PerfTracer

    @Inject
    lateinit var jankStatsTracker: JankStatsTracker

    @Inject
    lateinit var widgetDataObserver: WidgetDataObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        perfTracer.mark(TraceTags.ACTIVITY_ON_CREATE)
        jankStatsTracker.start(this)
        val payload = intent?.getStringExtra(EXTRA_TIMETABLE_JSON)?.takeIf { it.isNotBlank() }
        setContent {
            TimetableTheme {
                AppNavHost(autoImportPayload = payload)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 观察窗口限定在前台：避免把一次 SQLCipher 开库塞进冷启动路径
        widgetDataObserver.start(lifecycleScope)
    }

    override fun onStop() {
        widgetDataObserver.stop()
        super.onStop()
    }

    override fun onDestroy() {
        jankStatsTracker.stop()
        super.onDestroy()
    }

    companion object {
        /** 与 [EXTRA_TIMETABLE_JSON] 同时使用；单独给 JSON 也会触发导入，此开关仅为基准测试语义显式化。 */
        const val EXTRA_AUTO_IMPORT = "auto_import"

        const val EXTRA_TIMETABLE_JSON = "timetable_json"
    }
}
