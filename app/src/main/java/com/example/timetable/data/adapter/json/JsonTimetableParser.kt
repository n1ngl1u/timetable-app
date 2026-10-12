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
    val weeks: List<Int> = emptyList(),
)

/**
 * 通用课表 JSON 解析：字段缺失走默认值兜底，结构错误抛 [CourseParseException]。
 */
@Singleton
class JsonTimetableParser @Inject constructor(
    private val json: Json,
    private val timeProvider: TimeProvider,
) : TimetableParser {

    override fun parse(payload: String): Timetable {
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

    private fun TimetableJson.toDomain(today: LocalDate): Timetable = Timetable(
        tableName = tableName?.takeIf { it.isNotBlank() } ?: DEFAULT_TABLE_NAME,
        startDate = startDate?.takeIf { it.isNotBlank() }?.let(::parseDate) ?: today,
        timeSlots = timeSlots.mapIndexed { index, slot -> slot.toDomain(index) },
        courses = courses.mapIndexed { index, course -> course.toDomain(index) },
    )

    private fun TimeSlotJson.toDomain(index: Int): TimeSlot {
        val slotNumber = number ?: throw CourseParseException(
            message = "timeSlots[" + index + "].number 缺失",
            field = "timeSlots[" + index + "].number",
        )
        return TimeSlot(
            number = slotNumber,
            startTime = startTime.orEmpty(),
            endTime = endTime.orEmpty(),
        )
    }

    private fun CourseJson.toDomain(index: Int): Course {
        val courseName = name?.takeIf { it.isNotBlank() } ?: throw CourseParseException(
            message = "courses[" + index + "].name 缺失",
            field = "courses[" + index + "].name",
        )
        val courseWeekday = weekday ?: throw CourseParseException(
            message = "courses[" + index + "].weekday 缺失",
            field = "courses[" + index + "].weekday",
        )
        val firstSection = startSection ?: throw CourseParseException(
            message = "courses[" + index + "].startSection 缺失",
            field = "courses[" + index + "].startSection",
        )
        val lastSection = endSection ?: firstSection
        return try {
            Course(
                name = courseName,
                teacher = teacher.orEmpty(),
                location = location.orEmpty(),
                weekday = courseWeekday,
                startSection = firstSection,
                endSection = lastSection,
                weeks = weeks.distinct().sorted(),
            )
        } catch (error: IllegalArgumentException) {
            throw CourseParseException(
                message = "courses[" + index + "] 字段非法: " + (error.message ?: ""),
                field = "courses[" + index + "]",
                cause = error,
            )
        }
    }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (error: DateTimeParseException) {
        throw CourseParseException(message = "startDate 格式非法: " + value, cause = error)
    }

    private companion object {
        const val DEFAULT_TABLE_NAME = "我的课表"
    }
}
