package com.example.timetable.data.local.converter

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * weeks 以离散周次数组建模，落库为逗号分隔字符串；LocalDate 落库为 epochDay。
 */
class Converters {

    @TypeConverter
    fun fromWeeks(weeks: List<Int>): String = weeks.joinToString(separator = ",")

    @TypeConverter
    fun toWeeks(value: String): List<Int> =
        if (value.isBlank()) {
            emptyList()
        } else {
            value.split(',').mapNotNull { it.trim().toIntOrNull() }
        }

    @TypeConverter
    fun fromLocalDate(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}
