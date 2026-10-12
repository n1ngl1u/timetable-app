package com.example.timetable.di

import com.example.timetable.data.repository.CalendarRepositoryImpl
import com.example.timetable.data.repository.GradeRepositoryImpl
import com.example.timetable.data.repository.ReminderRepositoryImpl
import com.example.timetable.data.repository.SchoolRepositoryImpl
import com.example.timetable.data.repository.SettingsRepositoryImpl
import com.example.timetable.data.repository.TimetableRepositoryImpl
import com.example.timetable.domain.repository.CalendarRepository
import com.example.timetable.domain.repository.GradeRepository
import com.example.timetable.domain.repository.ReminderRepository
import com.example.timetable.domain.repository.SchoolRepository
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTimetableRepository(impl: TimetableRepositoryImpl): TimetableRepository

    @Binds
    @Singleton
    abstract fun bindSchoolRepository(impl: SchoolRepositoryImpl): SchoolRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindGradeRepository(impl: GradeRepositoryImpl): GradeRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository
}
