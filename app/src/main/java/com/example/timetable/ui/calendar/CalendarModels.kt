package com.example.timetable.ui.calendar

import java.time.LocalDate
import java.time.YearMonth

/** 月视图单元格：日期 + 当天课程数（0 不显示徽标）。 */
data class CalendarDayCell(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val courseCount: Int,
) {
    val showBadge: Boolean get() = courseCount > 0
}

/** 单日详情列表项。 */
data class DayCourseItem(
    val courseId: Long,
    val name: String,
    val location: String,
    val timeText: String,
)

/** 排课投影：UI 侧要用它给 pager 里任意可见月份算徽标，所以必须与「当前月」解耦。 */
data class ScheduledCourse(
    val id: Long,
    val name: String,
    val location: String,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
)

data class CourseScheduleIndex(
    val startDate: LocalDate,
    val timeSlots: Map<Int, Pair<String, String>>,
    val byWeekday: Map<Int, List<ScheduledCourse>>,
) {

    fun coursesOn(date: LocalDate): List<ScheduledCourse> {
        val daysFromStart = date.toEpochDay() - startDate.toEpochDay()
        if (daysFromStart < 0) return emptyList()
        val week = (daysFromStart / MonthGrid.DAYS_IN_WEEK).toInt() + 1
        return byWeekday[date.dayOfWeek.value]
            .orEmpty()
            .filter { course -> course.weeks.contains(week) }
            .sortedBy { it.startSection }
    }

    fun countsIn(month: YearMonth): Map<LocalDate, Int> =
        MonthGrid.weeksOf(month).flatten().associateWith { date -> coursesOn(date).size }

    fun timeTextOf(course: ScheduledCourse): String {
        val start = timeSlots[course.startSection]?.first
        val end = timeSlots[course.endSection]?.second
        return if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
            start + "-" + end
        } else if (course.startSection == course.endSection) {
            "第 " + course.startSection + " 节"
        } else {
            "第 " + course.startSection + "-" + course.endSection + " 节"
        }
    }
}

data class CalendarUiModel(
    val month: YearMonth,
    val index: CourseScheduleIndex?,
    val today: LocalDate,
    val selectedDate: LocalDate?,
    val selectedDayCourses: List<DayCourseItem>,
) {
    val hasSelectedDay: Boolean get() = selectedDate != null
}
