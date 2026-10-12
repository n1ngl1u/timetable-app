package com.example.timetable.domain.repository

import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.Semester
import com.example.timetable.domain.model.Timetable
import kotlinx.coroutines.flow.Flow

interface TimetableRepository {

    fun observeTimetables(): Flow<List<Timetable>>

    fun observeTimetable(timetableId: Long): Flow<Timetable?>

    fun observeCourses(timetableId: Long): Flow<List<Course>>

    /** 按课表计数；timetableId 为 null（无活跃课表）时直接返回 0，不查库。 */
    fun observeCourseCount(timetableId: Long?): Flow<Int>

    fun observeSemesters(): Flow<List<Semester>>

    suspend fun timetable(timetableId: Long): Timetable?

    suspend fun coursesOn(timetableId: Long, weekday: Int, week: Int): List<Course>

    /** 整表替换：同一事务内先删该 timetableId 下的旧课程与节次，再写入新数据。 */
    suspend fun upsertTimetable(timetable: Timetable): Long

    suspend fun deleteTimetable(timetableId: Long)

    /** 删除单门课。调用方必须先经 DeleteCourseUseCase 清掉系统日历事件。 */
    suspend fun deleteCourse(courseId: Long)

    suspend fun upsertSemester(semester: Semester): Long

    suspend fun setActiveSemester(semesterId: Long)
}
