package com.example.timetable.domain.model

/**
 * 节次时间映射；时间格式为 "HH:mm"。
 */
data class TimeSlot(
    val number: Int,
    val startTime: String,
    val endTime: String,
) {
    val isValid: Boolean get() = isValidTime(startTime) && isValidTime(endTime)

    companion object {
        private val TIME_PATTERN = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

        fun isValidTime(value: String): Boolean = TIME_PATTERN.matches(value)
    }
}
