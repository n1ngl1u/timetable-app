package com.example.timetable.domain.repository

import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.Timetable
import kotlinx.coroutines.flow.Flow

interface TimetableRepository {

    fun observeTimetables(): Flow<List<Timetable>>

    fun observeTimetable(timetableId: Long): Flow<Timetable?>

    fun observeCourses(timetableId: Long): Flow<List<Course>>

    /** 全库课程总数，供首页摘要与导入完成信号使用（避免为计数把整表读出来）。 */
    fun observeTotalCourseCount(): Flow<Int>

    suspend fun timetable(timetableId: Long): Timetable?

    suspend fun coursesOn(timetableId: Long, weekday: Int, week: Int): List<Course>

    suspend fun upsertTimetable(timetable: Timetable): Long

    suspend fun deleteTimetable(timetableId: Long)
}
