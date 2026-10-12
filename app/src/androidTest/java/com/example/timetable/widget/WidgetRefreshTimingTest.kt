package com.example.timetable.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.timetable.MainActivity
import com.example.timetable.widget.today.TodayCoursesWidget
import com.example.timetable.widget.week.WeekOverviewWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T0（数据变更）-> T1（两个 widget 的 updateAll() 都返回）时序断言，预算 500ms。
 *
 * 前置条件：桌面上必须已放置「今日课程」与「一周概览」。未放置时 GlanceAppWidget.updateAll()
 * 没有任何 GlanceId 可更新，会近乎空转返回，测出来的是伪数据——因此这里先断言实例存在，
 * 不满足就直接失败，不静默通过。
 */
@RunWith(AndroidJUnit4::class)
class WidgetRefreshTimingTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context get() = instrumentation.targetContext

    @Test
    fun dataChangeRefreshesBothWidgetsWithinBudget() {
        val manager = GlanceAppWidgetManager(context)
        val todayIds = runBlocking { manager.getGlanceIds(TodayCoursesWidget::class.java) }
        val weekIds = runBlocking { manager.getGlanceIds(WeekOverviewWidget::class.java) }
        assertTrue(
            "请先在桌面放置「今日课程」与「一周概览」小组件后再运行本测试；" +
                "当前 today=" + todayIds.size + " week=" + weekIds.size,
            todayIds.isNotEmpty() && weekIds.isNotEmpty(),
        )

        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("logcat -c")
        context.startActivity(
            Intent()
                .setClassName(context.packageName, MainActivity::class.java.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_TIMETABLE_JSON, SAMPLE_TIMETABLE_JSON),
        )

        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var logcat = ""
        var measured: Long? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            logcat = device.executeShellCommand("logcat -d -s " + PERF_LOG_TAG + ":I")
            measured = parseLastSpanDurationMillis(logcat)
            if (measured != null) break
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }

        assertNotNull(
            "未采集到 " + SPAN_NAME + " 打点，日志尾部:\n" + logcat.takeLast(1200),
            measured,
        )
        val duration = measured ?: Long.MAX_VALUE
        assertTrue(
            "小组件刷新超预算: " + duration + "ms > " + BUDGET_MILLIS + "ms",
            duration <= BUDGET_MILLIS,
        )
    }

    private fun parseLastSpanDurationMillis(logcat: String): Long? {
        val pattern = Regex("SPAN name=" + Regex.escape(SPAN_NAME) + " durationMs=([0-9]+)")
        return pattern.findAll(logcat).lastOrNull()?.groupValues?.get(1)?.toLongOrNull()
    }

    private companion object {
        const val PERF_LOG_TAG = "TimetablePerf"
        const val SPAN_NAME = "widget_refresh"
        const val BUDGET_MILLIS = 500L
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 300L

        val SAMPLE_TIMETABLE_JSON = """
            {
              "tableName": "小组件基准课表",
              "startDate": "2026-09-07",
              "timeSlots": [ { "number": 1, "startTime": "08:00", "endTime": "08:45" } ],
              "courses": [
                { "name": "课程1", "location": "教学楼 101", "weekday": 1, "startSection": 1, "endSection": 1, "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20] },
                { "name": "课程2", "location": "教学楼 202", "weekday": 2, "startSection": 1, "endSection": 1, "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20] },
                { "name": "课程3", "location": "教学楼 303", "weekday": 3, "startSection": 1, "endSection": 1, "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20] },
                { "name": "课程4", "location": "教学楼 404", "weekday": 4, "startSection": 1, "endSection": 1, "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20] },
                { "name": "课程5", "location": "教学楼 505", "weekday": 5, "startSection": 1, "endSection": 1, "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20] },
                { "name": "课程6", "location": "教学楼 606", "weekday": 6, "startSection": 1, "endSection": 1, "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20] },
                { "name": "课程7", "location": "教学楼 707", "weekday": 7, "startSection": 1, "endSection": 1, "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20] }
              ]
            }
        """.trimIndent()
    }
}
