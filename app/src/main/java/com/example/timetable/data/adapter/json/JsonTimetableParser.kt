package com.example.timetable.data.adapter.json

import com.example.timetable.core.time.TimeProvider
import com.example.timetable.domain.importer.TimetableParser
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
internal data class TimetableJson(
    val tableName: String? = null,
    val startDate: String? = null,
    val timeSlots: List<TimeSlotJson> = emptyList(),
    val courses: List<CourseJson> = emptyList(),
)

@Serializable
internal data class TimeSlotJson(
    val number: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
)

@Serializable
internal data class CourseJson(
    val name: String? = null,
    val teacher: String? = null,
    val location: String? = null,
    val weekday: Int? = null,
    val startSection: Int? = null,
    val endSection: Int? = null,
    // null = 字段缺失（走兜底）；空数组 = 显式声明本课程无排课（不兜底）
    val weeks: List<Int>? = null,
)

/**
 * 通用课表 JSON 解析。
 *
 * 错误契约（二选一，不混用）：
 * - 「字段缺失」-> CourseParseException.field 非空 -> 上层映射 MissingField；
 * - 「值非法 / 结构错误」-> field 为 null，字段路径写在 message 里 -> 上层映射 SchemaChanged。
 */
@Singleton
class JsonTimetableParser @Inject constructor(
    private val json: Json,
    private val timeProvider: TimeProvider,
) : TimetableParser {

    override fun parse(payload: String): Timetable {
        if (payload.isBlank()) {
            throw CourseParseException("课表载荷为空，无法解析")
        }
        val decoded = try {
            json.decodeFromString(TimetableJson.serializer(), payload)
        } catch (error: SerializationException) {
            throw CourseParseException(
                message = "课表 JSON 结构错误: " + (error.message ?: "无法解析"),
                cause = error,
            )
        } catch (error: IllegalArgumentException) {
            throw CourseParseException(
                message = "课表 JSON 结构错误: " + (error.message ?: "无法解析"),
                cause = error,
            )
        }
        return decoded.toDomain(timeProvider.today())
    }

    private fun TimetableJson.toDomain(today: LocalDate): Timetable {
        val otherCoursesMaxWeek = courses
            .mapNotNull { it.weeks }
            .filter { it.isNotEmpty() }
            .flatMap { it }
            .filter { it > 0 }
            .maxOrNull()
        val resolvedMaxWeek = otherCoursesMaxWeek ?: DEFAULT_TOTAL_WEEKS
        return Timetable(
            tableName = tableName?.takeIf { it.isNotBlank() } ?: DEFAULT_TABLE_NAME,
            startDate = startDate?.takeIf { it.isNotBlank() }?.let(::parseDate) ?: today,
            timeSlots = timeSlots.mapIndexed { index, slot -> slot.toDomain(index) },
            courses = courses.mapIndexed { index, course -> course.toDomain(index, resolvedMaxWeek) },
        )
    }

    private fun TimeSlotJson.toDomain(index: Int): TimeSlot {
        val path = "timeSlots[" + index + "]"
        val slotNumber = number
            ?: throw CourseParseException("字段缺失: " + path + ".number", field = path + ".number")
        return TimeSlot(
            number = slotNumber,
            startTime = normalizeTime(startTime),
            endTime = normalizeTime(endTime),
        )
    }

    private fun CourseJson.toDomain(index: Int, resolvedMaxWeek: Int): Course {
        val path = "courses[" + index + "]"
        val courseName = name?.takeIf { it.isNotBlank() }
            ?: throw CourseParseException("字段缺失: " + path + ".name", field = path + ".name")
        val courseWeekday = weekday
            ?: throw CourseParseException("字段缺失: " + path + ".weekday", field = path + ".weekday")
        if (courseWeekday !in 1..7) {
            throw CourseParseException(path + ".weekday 非法: " + courseWeekday + "，应在 1..7")
        }
        val firstSection = startSection
            ?: throw CourseParseException(
                "字段缺失: " + path + ".startSection",
                field = path + ".startSection",
            )
        if (firstSection < 1) {
            throw CourseParseException(path + ".startSection 非法: " + firstSection + "，应 >= 1")
        }
        val lastSection = endSection ?: firstSection
        if (lastSection < firstSection) {
            throw CourseParseException(
                path + ".endSection 非法: " + lastSection + " 小于 startSection " + firstSection,
            )
        }
        val rawWeeks = weeks
        val resolvedWeeks = if (rawWeeks == null) {
            // 单课程缺失 -> 用同学期其他课程的最大周次（整表都缺时 resolvedMaxWeek 已是 20）
            (1..resolvedMaxWeek).toList()
        } else {
            // 空数组保持为空：本课程无排课，不上屏
            rawWeeks.filter { it > 0 }.distinct().sorted()
        }
        return Course(
            name = courseName,
            teacher = teacher.orEmpty(),
            location = location.orEmpty(),
            weekday = courseWeekday,
            startSection = firstSection,
            endSection = lastSection,
            weeks = resolvedWeeks,
        )
    }

    /** "8:00" / "8：00" -> "08:00"；无法归一化的保留原文，TimeSlot.isValid 为 false 但不中断导入。 */
    private fun normalizeTime(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return value
        val match = TIME_PATTERN.matchEntire(value) ?: return value
        val hour = match.groupValues[1].toIntOrNull() ?: return value
        val minute = match.groupValues[2].toIntOrNull() ?: return value
        if (hour > 23 || minute > 59) return value
        val hourText = if (hour < 10) "0" + hour else hour.toString()
        val minuteText = if (minute < 10) "0" + minute else minute.toString()
        return hourText + ":" + minuteText
    }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value.replace('/', '-'))
    } catch (error: DateTimeParseException) {
        throw CourseParseException("startDate 格式非法: " + value + "，应为 yyyy-MM-dd", cause = error)
    }

    private companion object {
        const val DEFAULT_TABLE_NAME = "我的课表"
        const val DEFAULT_TOTAL_WEEKS = 20
        val TIME_PATTERN = Regex("^([0-9]{1,2})[:：]([0-9]{1,2})$")
    }
}
