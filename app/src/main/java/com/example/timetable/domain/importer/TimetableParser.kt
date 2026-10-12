package com.example.timetable.domain.importer

import com.example.timetable.domain.model.Timetable

/**
 * 文本载荷 -> 课表模型。结构错误抛
 * [com.example.timetable.data.adapter.json.CourseParseException]。
 */
interface TimetableParser {
    fun parse(payload: String): Timetable
}
