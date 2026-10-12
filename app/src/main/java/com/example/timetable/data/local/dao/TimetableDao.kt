package com.example.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.timetable.data.local.entity.SemesterEntity
import com.example.timetable.data.local.entity.TimetableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    @Query("SELECT * FROM timetables ORDER BY id ASC")
    fun observeAll(): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetables WHERE id = :timetableId")
    fun observeById(timetableId: Long): Flow<TimetableEntity?>

    @Query("SELECT * FROM timetables WHERE id = :timetableId")
    suspend fun findById(timetableId: Long): TimetableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TimetableEntity): Long

    @Update
    suspend fun update(entity: TimetableEntity)

    @Query("DELETE FROM timetables WHERE id = :timetableId")
    suspend fun deleteById(timetableId: Long)

    @Query("SELECT * FROM semesters ORDER BY id ASC")
    fun observeSemesters(): Flow<List<SemesterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemester(entity: SemesterEntity): Long

    @Query("UPDATE semesters SET is_active = (id = :semesterId)")
    suspend fun setActiveSemester(semesterId: Long)
}
