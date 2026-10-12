package com.example.timetable.ui.puremode

/**
 * 双击返回判定的纯函数。无 Android 依赖，无协程，可逐点单测。
 *
 * 不用「起一个 delay 协程到点重置」来表达超时：超时的语义就是「距上次返回已经超过窗口」，
 * 一次比较即可，多余的 Job 反而要跟着生命周期启停。
 */
object DoubleBackDetector {

    const val EXIT_WINDOW_MILLIS = 1_500L

    data class Evaluation(
        /** true 表示这是窗口内的第二次返回，调用方应当退出纯净模式。 */
        val shouldExit: Boolean,
        /** 应当记录的下一次「上次返回时间」；退出时清空。 */
        val nextLastBackMillis: Long?,
    )

    /**
     * [nowMillis] 与 [lastBackMillis] 都必须来自单调时钟（TimeProvider.elapsedRealtimeMillis）。
     * 墙钟可被用户与网络修改，用它算间隔会让双击窗口算错。
     */
    fun evaluate(nowMillis: Long, lastBackMillis: Long?): Evaluation {
        if (lastBackMillis == null) return Evaluation(shouldExit = false, nextLastBackMillis = nowMillis)
        // 时钟回退（理论上不该发生）当作一次全新按键，绝不让它触发退出
        if (nowMillis < lastBackMillis) return Evaluation(shouldExit = false, nextBackMillis(nowMillis))
        val elapsed = nowMillis - lastBackMillis
        return if (elapsed <= EXIT_WINDOW_MILLIS) {
            Evaluation(shouldExit = true, nextLastBackMillis = null)
        } else {
            Evaluation(shouldExit = false, nextLastBackMillis = nowMillis)
        }
    }

    private fun nextBackMillis(nowMillis: Long): Long? = nowMillis
}
