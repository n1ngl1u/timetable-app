package com.example.timetable.di

import com.example.timetable.data.local.dao.CourseDao
import com.example.timetable.data.local.dao.GradeDao
import com.example.timetable.data.local.dao.ReminderDao
import com.example.timetable.data.local.dao.TimeSlotDao
import com.example.timetable.data.local.dao.TimetableDao
import com.example.timetable.data.local.db.AppDatabase
import com.example.timetable.data.local.db.DatabaseFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(factory: DatabaseFactory): AppDatabase = factory.create()

    @Provides
    fun provideTimetableDao(database: AppDatabase): TimetableDao = database.timetableDao()

    @Provides
    fun provideCourseDao(database: AppDatabase): CourseDao = database.courseDao()

    @Provides
    fun provideTimeSlotDao(database: AppDatabase): TimeSlotDao = database.timeSlotDao()

    @Provides
    fun provideReminderDao(database: AppDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun provideGradeDao(database: AppDatabase): GradeDao = database.gradeDao()
}
