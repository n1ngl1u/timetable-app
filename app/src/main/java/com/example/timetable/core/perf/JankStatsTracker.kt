package com.example.timetable.core.perf

import android.app.Activity
import android.util.Log
import androidx.metrics.performance.JankStats
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JankStats 装配：rolling 统计每 [REPORT_INTERVAL_FRAMES] 帧输出一次 JANK_SUMMARY，
 * 使滚动卡顿率可在 Activity 存活期间被 androidTest 读取，并与 FrameTimingMetric 交叉校验。
 */
@Singleton
class JankStatsTracker @Inject constructor() {

    data class Summary(
        val totalFrames: Long,
        val jankFrames: Long,
    ) {
        val jankRatePercent: Double
            get() = if (totalFrames == 0L) {
                0.0
            } else {
                jankFrames.toDouble() * 100.0 / totalFrames.toDouble()
            }
    }

    private var jankStats: JankStats? = null
    private var totalFrames: Long = 0L
    private var jankFrames: Long = 0L

    fun start(activity: Activity) {
        if (jankStats != null) return
        totalFrames = 0L
        jankFrames = 0L
        jankStats = JankStats.createAndTrack(activity.window) { frameData ->
            totalFrames++
            if (frameData.isJank) {
                jankFrames++
                Log.w(
                    TraceTags.LOG_TAG,
                    "FRAME_JANK durationUiNanos=" + frameData.frameDurationUiNanos +
                        " states=" + frameData.states.joinToString(separator = "|"),
                )
            }
            if (totalFrames % REPORT_INTERVAL_FRAMES == 0L) {
                report()
            }
        }
    }

    fun stop(): Summary {
        jankStats?.isTrackingEnabled = false
        jankStats = null
        val summary = Summary(totalFrames = totalFrames, jankFrames = jankFrames)
        report()
        return summary
    }

    private fun report() {
        Log.i(TraceTags.LOG_TAG, "JANK_SUMMARY total=" + totalFrames + " jank=" + jankFrames)
    }

    private companion object {
        const val REPORT_INTERVAL_FRAMES = 120L
    }
}
