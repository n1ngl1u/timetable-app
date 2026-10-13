package com.example.timetable.ui.timetable

import com.example.timetable.domain.model.TimeSlot

/**
 * 课程行的时间文案。纯函数，无 Compose 依赖，可 JVM 单测。
 *
 * 四种回退语义：
 * - 有映射            -> "第 1-2 节（08:00-09:40）"
 * - timeSlots 为空    -> "第 1-2 节"
 * - timeSlots 里没有对应节次 -> "第 1-2 节"
 * - 单节次课程        -> "第 3 节（10:00-10:45）"
 *
 * 节次号**始终**显示，时间只在两端都能取到时追加：节次号是课表结构的一部分，
 * 有时间的课程也不该把它顶掉。
 */
object CourseTimeText {

    fun of(timeSlots: List<TimeSlot>, startSection: Int, endSection: Int): String {
        val sectionText = if (startSection == endSection) {
            "第 " + startSection + " 节"
        } else {
            "第 " + startSection + "-" + endSection + " 节"
        }
        val start = timeSlots.firstOrNull { slot -> slot.number == startSection }?.startTime
        val end = timeSlots.firstOrNull { slot -> slot.number == endSection }?.endTime
        if (start.isNullOrBlank() || end.isNullOrBlank()) {
            return sectionText
        }
        return sectionText + "（" + start + "-" + end + "）"
    }
}
