package com.example.timetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_event_links",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["course_id"]),
        Index(value = ["course_id", "segment_start_week"], unique = true),
    ],
)
data class CalendarEventLinkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "course_id")
    val courseId: Long,
    @ColumnInfo(name = "calendar_event_id")
    val calendarEventId: Long,
    @ColumnInfo(name = "segment_start_week")
    val segmentStartWeek: Int,
    @ColumnInfo(name = "segment_end_week")
    val segmentEndWeek: Int,
    @ColumnInfo(name = "content_hash")
    val contentHash: String,
    @ColumnInfo(name = "synced_at")
    val syncedAt: Long,
)
