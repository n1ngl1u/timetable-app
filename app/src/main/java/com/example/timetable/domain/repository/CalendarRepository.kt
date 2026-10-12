package com.example.timetable.domain.repository

import com.example.timetable.domain.calendar.CalendarEventLink
import com.example.timetable.domain.calendar.CalendarSyncPlan
import com.example.timetable.domain.calendar.CalendarSyncResult

/**
 * App -> 系统日历的单向写入（A7：不做双向）。
 *
 * 写入目标是设备默认账号下的专用日历「课程表」：首次调用时查询是否已存在，
 * 不存在则创建；不询问用户选账号。权限不足时所有方法返回 Skipped(PERMISSION_DENIED)
 * 或空结果，不抛 SecurityException。
 */
interface CalendarRepository {

    suspend fun hasCalendarPermissions(): Boolean

    /** 返回可写日历 id；权限不足或创建失败返回 null。并发安全。 */
    suspend fun ensureCourseCalendar(): Long?

    suspend fun linksForCourses(courseIds: List<Long>): List<CalendarEventLink>

    /** 应用一次增量计划；CalendarProvider 无跨条事务，失败走补偿式回滚。 */
    suspend fun applyChanges(plan: CalendarSyncPlan): CalendarSyncResult

    /**
     * 先删该课程在系统日历里的事件、再清 link 记录。
     * 课程删除路径必须先调本方法再删课程，否则外键 CASCADE 会先带走 link，
     * 系统日历里就留下永久孤儿事件。
     */
    suspend fun deleteEventsForCourse(courseId: Long): CalendarSyncResult

    /**
     * 自愈：删除「课程表」日历里所有不在 link 表中的事件，并清理指向已消失事件的 link。
     * 这是课程被整表替换（重新导入）后唯一能找回孤儿的手段。返回清理条数。
     */
    suspend fun pruneOrphanEvents(): Int

    /**
     * 作用域：清空整个「课程表」专用日历的所有事件 + 删除所有 link 记录。
     * 只影响本应用创建的日历，不动用户其他日历。
     */
    suspend fun clearSyncedEvents(): CalendarSyncResult
}
