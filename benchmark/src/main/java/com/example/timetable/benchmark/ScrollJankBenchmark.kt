package com.example.timetable.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 滚动 Jank 指标：FrameTimingMetric 官方测量 + JankStats 打点断言（< 1%）。
 */
@RunWith(AndroidJUnit4::class)
class ScrollJankBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollTimetableFrames() = benchmarkRule.measureRepeated(
        packageName = PerfAssertions.TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
    ) {
        startActivityAndWait()
        device.waitForIdle()
        repeat(SCROLL_TIMES) {
            scrollOnce(device)
            device.waitForIdle()
        }
    }

    @Test
    fun jankStatsRateWithinBudget() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("logcat -c")
        val context = InstrumentationRegistry.getInstrumentation().context
        context.startActivity(
            Intent()
                .setClassName(PerfAssertions.TARGET_PACKAGE, PerfAssertions.TARGET_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        device.wait(Until.hasObject(By.pkg(PerfAssertions.TARGET_PACKAGE)), LAUNCH_TIMEOUT_MS)
        repeat(SCROLL_TIMES) {
            scrollOnce(device)
            device.waitForIdle()
        }
        val logcat = device.executeShellCommand(
            "logcat -d -s " + PerfAssertions.PERF_LOG_TAG + ":I",
        )
        PerfAssertions.assertJankRateWithinBudget(
            PerfAssertions.parseLastJankSummary(logcat),
        )
    }

    private fun scrollOnce(device: UiDevice) {
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            SWIPE_STEPS,
        )
    }

    private companion object {
        const val SCROLL_TIMES = 6
        const val SWIPE_STEPS = 20
        const val LAUNCH_TIMEOUT_MS = 10_000L
    }
}
