package com.example.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.timetable.core.perf.JankStatsTracker
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import com.example.timetable.navigation.AppNavHost
import com.example.timetable.ui.theme.TimetableTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var perfTracer: PerfTracer

    @Inject
    lateinit var jankStatsTracker: JankStatsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        perfTracer.mark(TraceTags.ACTIVITY_ON_CREATE)
        jankStatsTracker.start(this)
        val autoImport = intent?.getBooleanExtra(EXTRA_AUTO_IMPORT, false) ?: false
        val payload = if (autoImport) intent?.getStringExtra(EXTRA_TIMETABLE_JSON) else null
        setContent {
            TimetableTheme {
                AppNavHost(autoImportPayload = payload)
            }
        }
    }

    override fun onDestroy() {
        jankStatsTracker.stop()
        super.onDestroy()
    }

    companion object {
        /** Macrobenchmark 稳定触发导入的入口参数。 */
        const val EXTRA_AUTO_IMPORT = "auto_import"

        const val EXTRA_TIMETABLE_JSON = "timetable_json"
    }
}
