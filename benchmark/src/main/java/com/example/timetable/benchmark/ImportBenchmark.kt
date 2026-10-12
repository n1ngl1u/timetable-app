package com.example.timetable.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 导入耗时：从触发导入到 UI 展示完整课表（import_first_frame 打点），预算 2000 ms。
 */
@RunWith(AndroidJUnit4::class)
class ImportBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun importTimetableFromJson() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("logcat -c")
        benchmarkRule.measureRepeated(
            packageName = PerfAssertions.TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 1,
        ) {
            startActivityAndWait(autoImportIntent())
            device.waitForIdle()
        }
        val logcat = device.executeShellCommand(
            "logcat -d -s " + PerfAssertions.PERF_LOG_TAG + ":I",
        )
        PerfAssertions.assertWithinBudget(
            metric = "课表导入",
            actualMillis = PerfAssertions.parseLastSpanDurationMillis(
                logcat,
                PerfAssertions.SPAN_IMPORT_FIRST_FRAME,
            ),
            budgetMillis = PerfAssertions.IMPORT_BUDGET_MS,
        )
    }

    private fun autoImportIntent(): Intent = Intent()
        .setClassName(PerfAssertions.TARGET_PACKAGE, PerfAssertions.TARGET_ACTIVITY)
        .putExtra(PerfAssertions.EXTRA_AUTO_IMPORT, true)
        .putExtra(PerfAssertions.EXTRA_TIMETABLE_JSON, PerfAssertions.SAMPLE_TIMETABLE_JSON)
}
