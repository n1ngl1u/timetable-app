package com.example.timetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = TimetableEntity::class,
            parentColumns = ["id"],
            childColumns = ["timetable_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["timetable_id"]),
        Index(value = ["timetable_id", "weekday"]),
    ],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "timetable_id")
    val timetableId: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "teacher")
    val teacher: String,
    @ColumnInfo(name = "location")
    val location: String,
    @ColumnInfo(name = "weekday")
    val weekday: Int,
    @ColumnInfo(name = "start_section")
    val startSection: Int,
    @ColumnInfo(name = "end_section")
    val endSection: Int,
    @ColumnInfo(name = "weeks")
    val weeks: List<Int>,
)
