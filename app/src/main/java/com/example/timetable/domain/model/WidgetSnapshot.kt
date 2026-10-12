package com.example.timetable.domain.model

/** 小组件只读快照模型：不含任何 Android / Glance 类型，可纯 JVM 单测。 */
data class WidgetCourseItem(
    val name: String,
    val location: String,
    val timeText: String,
)

data class TodaySnapshot(
    val tableName: String,
    val weekday: Int,
    val weekIndex: Int,
    val items: List<WidgetCourseItem>,
) {
    val hasCourses: Boolean get() = items.isNotEmpty()
}

data class WidgetDayColumn(
    val weekday: Int,
    val items: List<WidgetCourseItem>,
)

data class WeekSnapshot(
    val tableName: String,
    val weekIndex: Int,
    val days: List<WidgetDayColumn>,
) {
    val hasCourses: Boolean get() = days.any { it.items.isNotEmpty() }
}
