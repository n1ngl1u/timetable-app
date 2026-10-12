package com.example.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timetable.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses WHERE timetable_id = :timetableId ORDER BY weekday ASC, start_section ASC")
    fun observeByTimetable(timetableId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE timetable_id = :timetableId ORDER BY weekday ASC, start_section ASC")
    suspend fun findByTimetable(timetableId: Long): List<CourseEntity>

    @Query(
        "SELECT * FROM courses WHERE timetable_id = :timetableId AND weekday = :weekday " +
            "ORDER BY start_section ASC",
    )
    suspend fun findByWeekday(timetableId: Long, weekday: Int): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :courseId")
    suspend fun findById(courseId: Long): CourseEntity?

    /** 多课表下计数必须按课表过滤，全表计数会把其他课表的课程算进当前课表。 */
    @Query("SELECT COUNT(*) FROM courses WHERE timetable_id = :timetableId")
    fun observeCountByTimetable(timetableId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CourseEntity>): List<Long>

    @Query("DELETE FROM courses WHERE timetable_id = :timetableId")
    suspend fun deleteByTimetable(timetableId: Long)
}
