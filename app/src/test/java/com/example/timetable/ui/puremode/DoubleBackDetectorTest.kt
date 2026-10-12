package com.example.timetable.ui.puremode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleBackDetectorTest {

    private val window = DoubleBackDetector.EXIT_WINDOW_MILLIS

    @Test
    fun firstPressOnlyArmsTheWindow() {
        val result = DoubleBackDetector.evaluate(nowMillis = 1_000L, lastBackMillis = null)

        assertFalse(result.shouldExit)
        assertEquals(1_000L, result.nextLastBackMillis)
    }

    @Test
    fun secondPressExactlyAtWindowBoundaryExits() {
        val result = DoubleBackDetector.evaluate(nowMillis = 1_000L + window, lastBackMillis = 1_000L)

        assertTrue("恰好 1500ms 应当算窗口内", result.shouldExit)
        assertNull("退出后计时器清空", result.nextLastBackMillis)
    }

    @Test
    fun secondPressOneMillisecondLateRearms() {
        val result = DoubleBackDetector.evaluate(nowMillis = 1_000L + window + 1, lastBackMillis = 1_000L)

        assertFalse(result.shouldExit)
        assertEquals(1_000L + window + 1, result.nextLastBackMillis)
    }

    @Test
    fun twoSlowPressesNeverExit() {
        var last: Long? = null
        var now = 0L
        repeat(4) {
            now += window + 1
            val result = DoubleBackDetector.evaluate(now, last)
            assertFalse("每次都超窗口，永远不该退出", result.shouldExit)
            last = result.nextLastBackMillis
        }
    }

    @Test
    fun twoFastPressesExit() {
        var last: Long? = null
        var now = 0L
        var exited = false
        repeat(2) {
            now += 100
            val result = DoubleBackDetector.evaluate(now, last)
            last = result.nextLastBackMillis
            exited = result.shouldExit
        }

        assertTrue(exited)
        assertNull(last)
    }

    @Test
    fun pressAfterAnExitStartsAFreshWindow() {
        val exit = DoubleBackDetector.evaluate(2_000L, 1_000L)
        assertTrue(exit.shouldExit)

        val afterExit = DoubleBackDetector.evaluate(2_100L, exit.nextLastBackMillis)

        assertFalse("退出后第一次返回必须只是重新计时", afterExit.shouldExit)
        assertEquals(2_100L, afterExit.nextLastBackMillis)
    }

    @Test
    fun backwardsClockIsTreatedAsAFreshPress() {
        val result = DoubleBackDetector.evaluate(nowMillis = 500L, lastBackMillis = 1_000L)

        assertFalse("时钟回退绝不能触发退出", result.shouldExit)
        assertEquals(500L, result.nextLastBackMillis)
    }
}
