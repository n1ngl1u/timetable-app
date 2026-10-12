package com.example.timetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gpa_snapshots",
    indices = [Index(value = ["semester_id"])],
)
data class GpaSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "semester_id")
    val semesterId: Long,
    @ColumnInfo(name = "gpa")
    val gpa: Double,
    @ColumnInfo(name = "total_credits")
    val totalCredits: Double,
    @ColumnInfo(name = "course_count")
    val courseCount: Int,
)
