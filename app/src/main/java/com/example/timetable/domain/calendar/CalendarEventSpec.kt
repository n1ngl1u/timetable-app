package com.example.timetable.domain.calendar

/**
 * 一条待写入系统日历的事件规格。方案 A′：按「连续周次段」拆成多条事件，
 * 每段用 FREQ=WEEKLY;BYDAY=<DAY>;COUNT=<段长>，因此不需要 BYWEEKNO。
 */
data class CalendarEventSpec(
    val segmentStartWeek: Int,
    val segmentEndWeek: Int,
    val title: String,
    val location: String,
    val description: String,
    /** DTSTART，毫秒。 */
    val startMillis: Long,
    /** DTEND，毫秒。 */
    val endMillis: Long,
    val timeZone: String,
    val rrule: String?,
    val contentHash: String,
) {
    val segmentLength: Int get() = segmentEndWeek - segmentStartWeek + 1
}
