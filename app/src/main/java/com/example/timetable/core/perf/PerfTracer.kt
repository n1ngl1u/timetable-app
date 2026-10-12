package com.example.timetable.core.perf

import android.util.Log
import com.example.timetable.core.time.TimeProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 极轻量打点器：内存记录 + Logcat 输出，供 androidTest 解析时序。
 */
@Singleton
class PerfTracer @Inject constructor(
    private val timeProvider: TimeProvider,
) {

    data class Span(
        val name: String,
        val startMillis: Long,
        val endMillis: Long,
    ) {
        val durationMillis: Long get() = endMillis - startMillis
    }

    private val openMarks = ConcurrentHashMap<String, Long>()
    private val finished = ArrayDeque<Span>()
    private val lock = Any()

    fun mark(name: String): Long {
        val now = timeProvider.elapsedRealtimeMillis()
        openMarks[name] = now
        Log.i(TraceTags.LOG_TAG, "MARK name=" + name + " at=" + now)
        return now
    }

    fun end(name: String): Span? {
        val start = openMarks.remove(name) ?: return null
        return record(name, start)
    }

    fun span(name: String, startMillis: Long): Span = record(name, startMillis)

    fun snapshot(): List<Span> = synchronized(lock) { finished.toList() }

    fun lastSpan(name: String): Span? = synchronized(lock) { finished.lastOrNull { it.name == name } }

    private fun record(name: String, startMillis: Long): Span {
        val span = Span(
            name = name,
            startMillis = startMillis,
            endMillis = timeProvider.elapsedRealtimeMillis(),
        )
        synchronized(lock) {
            finished.addLast(span)
            while (finished.size > MAX_SPANS) {
                finished.removeFirst()
            }
        }
        Log.i(TraceTags.LOG_TAG, "SPAN name=" + name + " durationMs=" + span.durationMillis)
        return span
    }

    private companion object {
        const val MAX_SPANS = 128
    }
}
