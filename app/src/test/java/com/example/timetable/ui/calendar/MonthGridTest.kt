package com.example.timetable.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthGridTest {

    @Test
    fun everyWeekHasExactlySevenDays() {
        val weeks = MonthGrid.weeksOf(YearMonth.of(2026, 9))

        assertTrue(weeks.isNotEmpty())
        assertTrue(weeks.all { it.size == 7 })
    }

    @Test
    fun gridStartsOnMondayAndCoversWholeMonth() {
        val month = YearMonth.of(2026, 9)
        val weeks = MonthGrid.weeksOf(month)
        val flat = weeks.flatten()

        assertEquals(DayOfWeek.MONDAY, flat.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, flat.last().dayOfWeek)
        assertTrue(flat.contains(month.atDay(1)))
        assertTrue(flat.contains(month.atEndOfMonth()))
    }

    @Test
    fun september2026StartsWithLeadingAugustDays() {
        // 2026-09-01 是周二，因此首格是 2026-08-31
        val weeks = MonthGrid.weeksOf(YearMonth.of(2026, 9))

        assertEquals(LocalDate.of(2026, 8, 31), weeks.first().first())
        assertEquals(LocalDate.of(2026, 9, 1), weeks.first()[1])
    }

    @Test
    fun monthStartingOnMondayHasNoLeadingDays() {
        // 2026-06-01 是周一
        val weeks = MonthGrid.weeksOf(YearMonth.of(2026, 6))

        assertEquals(LocalDate.of(2026, 6, 1), weeks.first().first())
    }

    @Test
    fun monthEndingOnSundayHasNoTrailingDays() {
        // 2026-02-28 是周六；2027-02-28 是周日，取它验证无尾随补齐
        val weeks = MonthGrid.weeksOf(YearMonth.of(2027, 2))

        assertEquals(LocalDate.of(2027, 2, 28), weeks.last().last())
    }

    @Test
    fun leapYearFebruaryHas29Days() {
        val weeks = MonthGrid.weeksOf(YearMonth.of(2028, 2))
        val flat = weeks.flatten()

        assertTrue("2028 是闰年，应包含 2 月 29 日", flat.contains(LocalDate.of(2028, 2, 29)))
        assertEquals("2028-02-01 是周二，周一补齐后横跨 5 周", 5, weeks.size)
    }

    @Test
    fun nonLeapFebruaryHas28Days() {
        val flat = MonthGrid.weeksOf(YearMonth.of(2026, 2)).flatten()

        assertTrue(flat.contains(LocalDate.of(2026, 2, 28)))
        assertTrue(
            "2026 不是闰年，网格里不应出现 2 月 29 日",
            flat.none { it.monthValue == 2 && it.dayOfMonth == 29 },
        )
    }

    @Test
    fun customFirstDayOfWeekShiftsGrid() {
        val weeks = MonthGrid.weeksOf(YearMonth.of(2026, 9), firstDayOfWeek = DayOfWeek.SUNDAY)

        assertEquals(DayOfWeek.SUNDAY, weeks.first().first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 8, 30), weeks.first().first())
    }

    @Test
    fun dayCountIsAlwaysMultipleOfSeven() {
        listOf(2026 to 1, 2026 to 2, 2026 to 9, 2027 to 2, 2028 to 2, 2028 to 12).forEach { pair ->
            val weeks = MonthGrid.weeksOf(YearMonth.of(pair.first, pair.second))
            assertEquals(
                pair.toString(),
                0,
                weeks.flatten().size % MonthGrid.DAYS_IN_WEEK,
            )
        }
    }

    @Test
    fun decemberGridCrossesIntoNextYear() {
        val flat = MonthGrid.weeksOf(YearMonth.of(2026, 12)).flatten()

        assertTrue(flat.contains(LocalDate.of(2027, 1, 1)) || flat.last().year == 2026)
    }
}
