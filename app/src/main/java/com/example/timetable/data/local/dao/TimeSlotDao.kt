package com.example.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timetable.data.local.entity.TimeSlotEntity

@Dao
interface TimeSlotDao {

    @Query("SELECT * FROM time_slots WHERE timetable_id = :timetableId ORDER BY number ASC")
    suspend fun findByTimetable(timetableId: Long): List<TimeSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TimeSlotEntity>)

    @Query("DELETE FROM time_slots WHERE timetable_id = :timetableId")
    suspend fun deleteByTimetable(timetableId: Long)
}
