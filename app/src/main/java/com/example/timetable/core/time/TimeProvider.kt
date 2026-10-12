package com.example.timetable.core.time

import android.os.SystemClock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 可注入时间源，使「当前周 / 今日课程」在测试中可控。
 */
interface TimeProvider {
    fun epochMillis(): Long
    fun elapsedRealtimeMillis(): Long
    fun today(): LocalDate
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun epochMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
    override fun today(): LocalDate = LocalDate.now()
}
