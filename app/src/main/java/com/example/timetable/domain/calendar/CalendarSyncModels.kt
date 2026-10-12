package com.example.timetable.domain.calendar

/** 单门课的映射结果：要么产出若干事件规格，要么携带原因跳过。 */
sealed interface CourseEventMapping {

    val courseId: Long

    data class Mapped(
        override val courseId: Long,
        val specs: List<CalendarEventSpec>,
    ) : CourseEventMapping

    data class Skipped(
        override val courseId: Long,
        val courseName: String,
        val reason: CalendarSkipReason,
    ) : CourseEventMapping
}

data class SkippedCourse(
    val courseId: Long,
    val courseName: String,
    val reason: CalendarSkipReason,
)

data class CalendarEventInsert(
    val courseId: Long,
    val spec: CalendarEventSpec,
)

data class CalendarEventUpdate(
    val courseId: Long,
    val calendarEventId: Long,
    val spec: CalendarEventSpec,
)

/** 纯 diff 结果：新增 / 修改 / 删除 / 未变四态。 */
data class CalendarSyncPlan(
    val inserts: List<CalendarEventInsert>,
    val updates: List<CalendarEventUpdate>,
    val deletes: List<CalendarEventLink>,
    val unchanged: List<CalendarEventLink>,
    val skippedCourses: List<SkippedCourse>,
) {
    val isEmpty: Boolean
        get() = inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty()

    val totalChanges: Int get() = inserts.size + updates.size + deletes.size
}

data class CalendarSyncOutcome(
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val unchanged: Int = 0,
    val prunedOrphans: Int = 0,
    /** 写入系统日历失败且补偿也未能完全清理的条数。 */
    val failed: Int = 0,
    val skippedCourses: List<SkippedCourse> = emptyList(),
) {
    val skippedCount: Int get() = skippedCourses.size
}

sealed interface CalendarSyncResult {

    data class Synced(val outcome: CalendarSyncOutcome) : CalendarSyncResult

    data class Skipped(
        val reason: CalendarSkipReason,
        val detail: String = "",
    ) : CalendarSyncResult

    /** 已经过补偿式回滚的写失败；不是权限问题，因此不混进 Skipped。 */
    data class Failed(val message: String) : CalendarSyncResult
}

enum class CalendarSkipReason {
    /** READ_CALENDAR / WRITE_CALENDAR 未授予：降级为仅本地展示，不抛异常。 */
    PERMISSION_DENIED,
    NO_ACTIVE_TIMETABLE,
    NO_COURSES,
    /** 找不到可写日历，且创建失败。 */
    NO_WRITABLE_CALENDAR,
    /** 该课程的节次在 timeSlots 里没有时间映射。 */
    MISSING_TIME_SLOT,
    /** 课程 weeks 为空：本身无排课。 */
    NO_SCHEDULED_WEEKS,
}
