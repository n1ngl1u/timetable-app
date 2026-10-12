package com.example.timetable.domain.repository

/**
 * 单向同步：App -> 系统日历，写入设备默认账号下的「课程表」专用日历。
 */
interface CalendarRepository {

    suspend fun syncTimetable(timetableId: Long): Int

    suspend fun clearSyncedEvents(): Int
}
