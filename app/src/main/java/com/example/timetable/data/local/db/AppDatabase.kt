package com.example.timetable.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.timetable.data.local.converter.Converters
import com.example.timetable.data.local.dao.CalendarEventLinkDao
import com.example.timetable.data.local.dao.CourseDao
import com.example.timetable.data.local.dao.GradeDao
import com.example.timetable.data.local.dao.ReminderDao
import com.example.timetable.data.local.dao.TimeSlotDao
import com.example.timetable.data.local.dao.TimetableDao
import com.example.timetable.data.local.entity.CalendarEventLinkEntity
import com.example.timetable.data.local.entity.CourseEntity
import com.example.timetable.data.local.entity.GpaSnapshotEntity
import com.example.timetable.data.local.entity.GradeEntity
import com.example.timetable.data.local.entity.ReminderEntity
import com.example.timetable.data.local.entity.SemesterEntity
import com.example.timetable.data.local.entity.TimeSlotEntity
import com.example.timetable.data.local.entity.TimetableEntity

@Database(
    entities = [
        SemesterEntity::class,
        TimetableEntity::class,
        CourseEntity::class,
        TimeSlotEntity::class,
        ReminderEntity::class,
        CalendarEventLinkEntity::class,
        GradeEntity::class,
        GpaSnapshotEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun timetableDao(): TimetableDao

    abstract fun courseDao(): CourseDao

    abstract fun timeSlotDao(): TimeSlotDao

    abstract fun reminderDao(): ReminderDao

    abstract fun calendarEventLinkDao(): CalendarEventLinkDao

    abstract fun gradeDao(): GradeDao

    companion object {
        const val NAME = "timetable.db"
    }
}
