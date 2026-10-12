package com.example.timetable.domain.usecase

import com.example.timetable.core.time.TimeProvider
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.model.TodaySnapshot
import com.example.timetable.domain.model.WeekSnapshot
import com.example.timetable.domain.model.WidgetCourseItem
import com.example.timetable.domain.model.WidgetDayColumn
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * 把活跃课表组装成小组件快照。周次由 startDate 推导（startDate 为第 1 周周一），
 * 与 [Timetable.sectionsOf] 的过滤规则一致，两个小组件共用同一数据源。
 *
 * 活跃课表为空时返回空快照：多课表语义下不再回退到「第一张课表」，
 * 否则删除活跃课表后小组件会静默显示另一张课表的内容。
 */
class BuildWidgetSnapshotUseCase @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) {

    suspend fun today(): TodaySnapshot {
        val today = timeProvider.today()
        val timetable = activeTimetable()
            ?: return TodaySnapshot(
                tableName = "",
                weekday = today.dayOfWeek.value,
                weekIndex = 0,
                items = emptyList(),
            )
        val weekIndex = weekIndexOf(timetable, today)
        val weekday = today.dayOfWeek.value
        return TodaySnapshot(
            tableName = timetable.tableName,
            weekday = weekday,
            weekIndex = weekIndex,
            items = timetable.sectionsOf(weekday, weekIndex).map { it.toWidgetItem(timetable) },
        )
    }

    suspend fun week(): WeekSnapshot {
        val today = timeProvider.today()
        val timetable = activeTimetable()
            ?: return WeekSnapshot(tableName = "", weekIndex = 0, days = emptyList())
        val weekIndex = weekIndexOf(timetable, today)
        val days = (1..DAYS_IN_WEEK).map { weekday ->
            WidgetDayColumn(
                weekday = weekday,
                items = timetable.sectionsOf(weekday, weekIndex).map { it.toWidgetItem(timetable) },
            )
        }
        return WeekSnapshot(tableName = timetable.tableName, weekIndex = weekIndex, days = days)
    }

    private suspend fun activeTimetable(): Timetable? {
        val activeId = settingsRepository.current().activeTimetableId ?: return null
        return timetableRepository.timetable(activeId)
    }

    private fun weekIndexOf(timetable: Timetable, date: LocalDate): Int {
        val daysFromStart = ChronoUnit.DAYS.between(timetable.startDate, date)
        if (daysFromStart < 0) return 1
        return (daysFromStart / DAYS_IN_WEEK).toInt() + 1
    }

    private fun Course.toWidgetItem(timetable: Timetable): WidgetCourseItem = WidgetCourseItem(
        name = name,
        location = location,
        timeText = timeTextOf(timetable, startSection, endSection),
    )

    private fun timeTextOf(timetable: Timetable, startSection: Int, endSection: Int): String {
        val start = timetable.timeSlots.firstOrNull { it.number == startSection }?.startTime
        val end = timetable.timeSlots.firstOrNull { it.number == endSection }?.endTime
        return if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
            start + "-" + end
        } else {
            sectionText(startSection, endSection)
        }
    }

    private fun sectionText(startSection: Int, endSection: Int): String =
        if (startSection == endSection) {
            "第" + startSection + "节"
        } else {
            "第" + startSection + "-" + endSection + "节"
        }

    companion object {
        const val DAYS_IN_WEEK = 7
    }
}
