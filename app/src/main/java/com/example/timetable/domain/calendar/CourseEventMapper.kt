package com.example.timetable.domain.calendar

import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.Timetable
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 纯映射：课程 + 课表 -> 事件规格列表。无 Android 依赖，now 无关，可 JVM 单测。
 *
 * 重复规则采用方案 A′：把离散 weeks 切成连续段，每段一条事件，
 * RRULE = FREQ=WEEKLY;BYDAY=<DAY>;COUNT=<段长>。跨年由 LocalDate.plusWeeks 自然处理。
 */
object CourseEventMapper {

    private val BY_DAY = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    fun map(timetable: Timetable, course: Course, zoneId: ZoneId): CourseEventMapping {
        val weeks = course.weeks.filter { it > 0 }.distinct().sorted()
        if (weeks.isEmpty()) {
            return CourseEventMapping.Skipped(course.id, course.name, CalendarSkipReason.NO_SCHEDULED_WEEKS)
        }
        val startTime = startTimeOf(timetable, course.startSection)
        val endTime = endTimeOf(timetable, course.endSection)
        if (startTime == null || endTime == null) {
            return CourseEventMapping.Skipped(course.id, course.name, CalendarSkipReason.MISSING_TIME_SLOT)
        }
        val zoneIdText = zoneText(zoneId)
        val specs = contiguousRuns(weeks).map { run ->
            val date = dateOf(timetable.startDate, course.weekday, run.first)
            val startMillis = date.atTime(startTime).atZone(zoneId).toInstant().toEpochMilli()
            val endMillis = date.atTime(endTime).atZone(zoneId).toInstant().toEpochMilli()
            val rrule = buildRrule(course.weekday, run.last - run.first + 1)
            CalendarEventSpec(
                segmentStartWeek = run.first,
                segmentEndWeek = run.last,
                title = course.name,
                location = course.location,
                description = descriptionOf(course),
                startMillis = startMillis,
                endMillis = endMillis,
                timeZone = zoneIdText,
                rrule = rrule,
                contentHash = contentHash(
                    title = course.name,
                    location = course.location,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    rrule = rrule,
                    timeZone = zoneIdText,
                ),
            )
        }
        return CourseEventMapping.Mapped(course.id, specs)
    }

    /**
     * CalendarContract 的 EVENT_TIMEZONE 期望 TZID；ZoneOffset.UTC 的 id 是 "Z"，
     * 归一成 "UTC" 以免被系统日历忽略时区。
     */
    fun zoneText(zoneId: ZoneId): String = if (zoneId.id == "Z") "UTC" else zoneId.id

    /** 离散周次 -> 连续段。如 [1,2,3,7,8] -> [[1..3],[7..8]]。 */
    fun contiguousRuns(weeks: List<Int>): List<IntRange> {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val runs = mutableListOf<IntRange>()
        var start = sorted.first()
        var previous = sorted.first()
        for (index in 1 until sorted.size) {
            val current = sorted[index]
            if (current == previous + 1) {
                previous = current
            } else {
                runs += start..previous
                start = current
                previous = current
            }
        }
        runs += start..previous
        return runs
    }

    fun buildRrule(weekday: Int, count: Int): String {
        val day = BY_DAY.getOrElse(weekday - 1) { BY_DAY[0] }
        return "FREQ=WEEKLY;BYDAY=" + day + ";COUNT=" + count
    }

    /** 第 week 周的该 weekday 的日期；week 从 1 开始，startDate 是第 1 周周一。 */
    fun dateOf(startDate: LocalDate, weekday: Int, week: Int): LocalDate =
        startDate.plusWeeks((week - 1).toLong()).plusDays((weekday - 1).toLong())

    /**
     * 确定性内容指纹：显式拼接 标题 | 地点 | DTSTART | DTEND | RRULE | 时区，
     * 再取 SHA-256 十六进制。刻意不含 courseId —— 课程换 id 但内容相同不应触发重写。
     */
    fun contentHash(
        title: String,
        location: String,
        startMillis: Long,
        endMillis: Long,
        rrule: String,
        timeZone: String,
    ): String {
        val canonical = listOf(
            title,
            location,
            startMillis.toString(),
            endMillis.toString(),
            rrule,
            timeZone,
        ).joinToString(separator = "\u0001")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /** 开始时间取 startSection 的 startTime。 */
    private fun startTimeOf(timetable: Timetable, section: Int): LocalTime? =
        parseTime(timetable.timeSlots.firstOrNull { it.number == section }?.startTime)

    /** 结束时间取 endSection 的 endTime —— 取成该节次的 startTime 会让事件提前 45 分钟结束。 */
    private fun endTimeOf(timetable: Timetable, section: Int): LocalTime? =
        parseTime(timetable.timeSlots.firstOrNull { it.number == section }?.endTime)

    private fun parseTime(raw: String?): LocalTime? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return try {
            LocalTime.parse(value)
        } catch (error: DateTimeParseException) {
            null
        }
    }

    private fun descriptionOf(course: Course): String = listOf(course.teacher, course.location)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator = " / ")
}
