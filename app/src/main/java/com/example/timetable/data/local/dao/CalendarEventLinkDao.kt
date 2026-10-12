package com.example.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timetable.data.local.entity.CalendarEventLinkEntity

@Dao
interface CalendarEventLinkDao {

    @Query("SELECT * FROM calendar_event_links ORDER BY course_id ASC, segment_start_week ASC")
    suspend fun findAll(): List<CalendarEventLinkEntity>

    @Query(
        "SELECT * FROM calendar_event_links WHERE course_id IN (:courseIds) " +
            "ORDER BY course_id ASC, segment_start_week ASC",
    )
    suspend fun findByCourseIds(courseIds: List<Long>): List<CalendarEventLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CalendarEventLinkEntity>)

    @Query("DELETE FROM calendar_event_links WHERE calendar_event_id IN (:eventIds)")
    suspend fun deleteByEventIds(eventIds: List<Long>)

    @Query("DELETE FROM calendar_event_links WHERE course_id = :courseId")
    suspend fun deleteByCourse(courseId: Long)

    @Query("DELETE FROM calendar_event_links")
    suspend fun deleteAll()
}
