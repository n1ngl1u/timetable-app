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

    @Query("SELECT COUNT(*) FROM courses")
    fun observeTotalCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CourseEntity>): List<Long>

    @Query("DELETE FROM courses WHERE timetable_id = :timetableId")
    suspend fun deleteByTimetable(timetableId: Long)
}
