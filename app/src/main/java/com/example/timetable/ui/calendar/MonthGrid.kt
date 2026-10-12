package com.example.timetable.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 纯月网格生成：无 Compose / Android 依赖，可 JVM 单测。
 * 输出整周列表（每周恰好 7 天），首尾用相邻月份补齐；跨月与闰年由 LocalDate 承担。
 */
object MonthGrid {

    const val DAYS_IN_WEEK = 7

    fun weeksOf(
        month: YearMonth,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): List<List<LocalDate>> {
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - firstDayOfWeek.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
        val gridStart = first.minusDays(leading.toLong())
        val last = month.atEndOfMonth()
        val trailing = (firstDayOfWeek.value - last.dayOfWeek.value + DAYS_IN_WEEK - 1) % DAYS_IN_WEEK
        val gridEnd = last.plusDays(trailing.toLong())
        val dayCount = (gridEnd.toEpochDay() - gridStart.toEpochDay()).toInt() + 1
        return (0 until dayCount)
            .map { gridStart.plusDays(it.toLong()) }
            .chunked(DAYS_IN_WEEK)
    }
}
