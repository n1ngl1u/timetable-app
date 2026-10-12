package com.example.timetable.di

import com.example.timetable.data.adapter.DefaultSchoolRegistry
import com.example.timetable.data.adapter.json.JsonTimetableParser
import com.example.timetable.data.adapter.local.LocalFileSchoolAdapter
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolRegistry
import com.example.timetable.domain.importer.TimetableParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdapterModule {

    @Binds
    @Singleton
    abstract fun bindTimetableParser(impl: JsonTimetableParser): TimetableParser

    @Binds
    @Singleton
    abstract fun bindSchoolRegistry(impl: DefaultSchoolRegistry): SchoolRegistry

    @Binds
    @IntoSet
    abstract fun bindLocalFileAdapter(impl: LocalFileSchoolAdapter): SchoolAdapter
}
