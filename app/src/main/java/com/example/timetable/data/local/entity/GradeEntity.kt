package com.example.timetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grades",
    indices = [Index(value = ["semester_id"])],
)
data class GradeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "semester_id")
    val semesterId: Long,
    @ColumnInfo(name = "course_name")
    val courseName: String,
    @ColumnInfo(name = "credit")
    val credit: Double,
    @ColumnInfo(name = "score")
    val score: Double,
    @ColumnInfo(name = "grade_point")
    val gradePoint: Double?,
)
