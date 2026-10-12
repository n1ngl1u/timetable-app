package com.example.timetable.domain.calendar

/**
 * 纯 diff：已有 link × 期望规格 -> 四态计划。按 (courseId, segmentStartWeek) 对齐，
 * 只有 contentHash 或段范围变了才算「修改」，因此重复导入同一份课表不会重写系统日历。
 */
object CalendarSyncPlanner {

    fun plan(
        mappings: List<CourseEventMapping>,
        existingLinks: List<CalendarEventLink>,
    ): CalendarSyncPlan {
        val linksByCourse = existingLinks.groupBy { it.courseId }
        val inserts = mutableListOf<CalendarEventInsert>()
        val updates = mutableListOf<CalendarEventUpdate>()
        val deletes = mutableListOf<CalendarEventLink>()
        val unchanged = mutableListOf<CalendarEventLink>()
        val skipped = mutableListOf<SkippedCourse>()

        for (mapping in mappings) {
            val links = linksByCourse[mapping.courseId].orEmpty()
            when (mapping) {
                is CourseEventMapping.Skipped -> {
                    skipped += SkippedCourse(mapping.courseId, mapping.courseName, mapping.reason)
                    // 课程本次不可映射（例如时间映射被删），已有事件必须清掉，否则留下错时间的事件
                    deletes += links
                }
                is CourseEventMapping.Mapped -> {
                    val linkByWeek = links.associateBy { it.segmentStartWeek }
                    val desiredWeeks = mutableSetOf<Int>()
                    for (spec in mapping.specs) {
                        desiredWeeks += spec.segmentStartWeek
                        val existing = linkByWeek[spec.segmentStartWeek]
                        when {
                            existing == null -> inserts += CalendarEventInsert(mapping.courseId, spec)
                            existing.contentHash == spec.contentHash &&
                                existing.segmentEndWeek == spec.segmentEndWeek -> unchanged += existing
                            else -> updates += CalendarEventUpdate(
                                courseId = mapping.courseId,
                                calendarEventId = existing.calendarEventId,
                                spec = spec,
                            )
                        }
                    }
                    links.filterNot { desiredWeeks.contains(it.segmentStartWeek) }.forEach { deletes += it }
                }
            }
        }

        return CalendarSyncPlan(
            inserts = inserts,
            updates = updates,
            deletes = deletes,
            unchanged = unchanged,
            skippedCourses = skipped,
        )
    }
}
