package com.example.timetable.benchmark

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * 指标预算与日志打点解析。预算值必须与 core/perf/TraceTags.kt 保持一致。
 */
object PerfAssertions {

    const val TARGET_PACKAGE = "com.example.timetable"
    const val TARGET_ACTIVITY = "com.example.timetable.MainActivity"
    const val PERF_LOG_TAG = "TimetablePerf"

    const val COLD_START_BUDGET_MS = 800L
    const val IMPORT_BUDGET_MS = 2_000L
    const val WIDGET_REFRESH_BUDGET_MS = 500L
    const val MAX_JANK_RATE_PERCENT = 1.0

    const val EXTRA_AUTO_IMPORT = "auto_import"
    const val EXTRA_TIMETABLE_JSON = "timetable_json"

    const val SPAN_IMPORT_FIRST_FRAME = "import_first_frame"
    const val SPAN_WIDGET_REFRESH = "widget_refresh"

    /** 导入基准使用的样例课表（与 docs 中的数据格式一致）。 */
    val SAMPLE_TIMETABLE_JSON = """
        {
          "tableName": "2026 秋 计算机科学与技术",
          "startDate": "2026-09-07",
          "timeSlots": [
            { "number": 1, "startTime": "08:00", "endTime": "08:45" },
            { "number": 2, "startTime": "08:55", "endTime": "09:40" },
            { "number": 3, "startTime": "10:00", "endTime": "10:45" },
            { "number": 4, "startTime": "10:55", "endTime": "11:40" },
            { "number": 5, "startTime": "14:00", "endTime": "14:45" },
            { "number": 6, "startTime": "14:55", "endTime": "15:40" }
          ],
          "courses": [
            { "name": "高等数学", "teacher": "张伟", "location": "教一 101", "weekday": 1, "startSection": 1, "endSection": 2, "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16] },
            { "name": "大学英语", "teacher": "李娜", "location": "外语楼 305", "weekday": 1, "startSection": 3, "endSection": 4, "weeks": [1,3,5,7,9,11,13,15] },
            { "name": "数据结构", "teacher": "王强", "location": "计算机楼 402", "weekday": 2, "startSection": 1, "endSection": 2, "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16] },
            { "name": "操作系统", "teacher": "陈晨", "location": "计算机楼 210", "weekday": 3, "startSection": 5, "endSection": 6, "weeks": [2,4,6,8,10,12,14,16] },
            { "name": "计算机网络", "teacher": "刘洋", "location": "计算机楼 315", "weekday": 4, "startSection": 3, "endSection": 4, "weeks": [1,2,3,4,5,6,7,8] },
            { "name": "体育", "teacher": "赵磊", "location": "体育馆", "weekday": 5, "startSection": 5, "endSection": 6, "weeks": [1,3,5,7,9,11,13,15] }
          ]
        }
    """.trimIndent()

    fun parseLastSpanDurationMillis(logcat: String, spanName: String): Long? {
        val pattern = Regex("SPAN name=" + Regex.escape(spanName) + " durationMs=([0-9]+)")
        return pattern.findAll(logcat).lastOrNull()?.groupValues?.get(1)?.toLongOrNull()
    }

    fun parseLastJankSummary(logcat: String): Pair<Long, Long>? {
        val pattern = Regex("JANK_SUMMARY total=([0-9]+) jank=([0-9]+)")
        val match = pattern.findAll(logcat).lastOrNull() ?: return null
        val total = match.groupValues[1].toLongOrNull() ?: return null
        val jank = match.groupValues[2].toLongOrNull() ?: return null
        return total to jank
    }

    fun assertWithinBudget(metric: String, actualMillis: Long?, budgetMillis: Long) {
        assertNotNull(metric + " 未采集到打点结果", actualMillis)
        val actual = actualMillis ?: 0L
        assertTrue(
            metric + " 超预算: " + actual + "ms > " + budgetMillis + "ms",
            actual <= budgetMillis,
        )
    }

    fun assertJankRateWithinBudget(summary: Pair<Long, Long>?) {
        assertNotNull("未采集到 JANK_SUMMARY 打点结果", summary)
        val (totalFrames, jankFrames) = summary ?: (0L to 0L)
        assertTrue("JankStats 未采集到任何帧", totalFrames > 0L)
        val rate = jankFrames.toDouble() * 100.0 / totalFrames.toDouble()
        assertTrue(
            "滚动 Jank 率超预算: " + rate + "% >= " + MAX_JANK_RATE_PERCENT + "%",
            rate < MAX_JANK_RATE_PERCENT,
        )
    }
}
