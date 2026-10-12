package com.example.timetable.ui.theme

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayPeriodTest {

    @Test
    fun allEightBoundaryPointsResolveAsSpecified() {
        assertEquals(DayPeriod.NIGHT, DayPeriodResolver.resolve(LocalTime.of(5, 59)))
        assertEquals(DayPeriod.MORNING, DayPeriodResolver.resolve(LocalTime.of(6, 0)))
        assertEquals(DayPeriod.MORNING, DayPeriodResolver.resolve(LocalTime.of(10, 59)))
        assertEquals(DayPeriod.AFTERNOON, DayPeriodResolver.resolve(LocalTime.of(11, 0)))
        assertEquals(DayPeriod.AFTERNOON, DayPeriodResolver.resolve(LocalTime.of(16, 59)))
        assertEquals(DayPeriod.DUSK, DayPeriodResolver.resolve(LocalTime.of(17, 0)))
        assertEquals(DayPeriod.DUSK, DayPeriodResolver.resolve(LocalTime.of(19, 59)))
        assertEquals(DayPeriod.NIGHT, DayPeriodResolver.resolve(LocalTime.of(20, 0)))
    }

    @Test
    fun midnightAndLastMinuteOfDayAreNight() {
        assertEquals(DayPeriod.NIGHT, DayPeriodResolver.resolve(LocalTime.MIDNIGHT))
        assertEquals(DayPeriod.NIGHT, DayPeriodResolver.resolve(LocalTime.of(23, 59, 59)))
    }

    @Test
    fun everyMinuteOfDayResolvesToExactlyOnePeriod() {
        var minute = 0
        while (minute < MINUTES_PER_DAY) {
            val time = LocalTime.of(0, 0).plusMinutes(minute.toLong())
            val period = DayPeriodResolver.resolve(time)
            assertEquals(
                "时间 " + time + " 不应落在四态之外",
                true,
                DayPeriod.entries.contains(period),
            )
            minute += 7
        }
    }

    @Test
    fun boundaryConstantsAreOrderedAndConfigurable() {
        assertEquals(LocalTime.of(6, 0), DayPeriodResolver.MORNING_START)
        assertEquals(LocalTime.of(11, 0), DayPeriodResolver.AFTERNOON_START)
        assertEquals(LocalTime.of(17, 0), DayPeriodResolver.DUSK_START)
        assertEquals(LocalTime.of(20, 0), DayPeriodResolver.NIGHT_START)
        assertEquals(
            true,
            DayPeriodResolver.MORNING_START < DayPeriodResolver.AFTERNOON_START &&
                DayPeriodResolver.AFTERNOON_START < DayPeriodResolver.DUSK_START &&
                DayPeriodResolver.DUSK_START < DayPeriodResolver.NIGHT_START,
        )
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
