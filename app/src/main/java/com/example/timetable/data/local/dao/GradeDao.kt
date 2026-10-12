package com.example.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timetable.data.local.entity.GpaSnapshotEntity
import com.example.timetable.data.local.entity.GradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GradeDao {

    @Query("SELECT * FROM grades WHERE semester_id = :semesterId ORDER BY course_name ASC")
    fun observeBySemester(semesterId: Long): Flow<List<GradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<GradeEntity>)

    @Query("DELETE FROM grades WHERE semester_id = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)

    @Query("SELECT * FROM gpa_snapshots WHERE semester_id = :semesterId ORDER BY id ASC")
    fun observeSnapshots(semesterId: Long): Flow<List<GpaSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(entity: GpaSnapshotEntity): Long
}
