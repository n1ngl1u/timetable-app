package com.example.timetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["course_id"])],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "course_id")
    val courseId: Long,
    @ColumnInfo(name = "advance_minutes")
    val advanceMinutes: Int,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    /** 存储格式与 courses.weeks 完全一致：逗号分隔的周次，空串表示未指定。 */
    @ColumnInfo(name = "weeks")
    val weeks: List<Int>,
)
