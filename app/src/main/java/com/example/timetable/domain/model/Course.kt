package com.example.timetable.domain.model

/**
 * 课程领域模型。weekday 取 1..7（周一为 1），startSection/endSection 为闭区间节次。
 */
data class Course(
    val id: Long = 0L,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int> = emptyList(),
) {
    init {
        require(weekday in 1..7) { "weekday 必须在 1..7，实际为 " + weekday }
        require(startSection >= 1) { "startSection 必须 >= 1，实际为 " + startSection }
        require(endSection >= startSection) { "endSection 不能小于 startSection" }
    }

    val sectionCount: Int get() = endSection - startSection + 1

    fun isActiveInWeek(week: Int): Boolean = weeks.contains(week)
}
