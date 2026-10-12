package com.example.timetable.data.mapper

import com.example.timetable.data.local.entity.CourseEntity
import com.example.timetable.data.local.entity.SemesterEntity
import com.example.timetable.data.local.entity.TimeSlotEntity
import com.example.timetable.data.local.entity.TimetableEntity
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.Semester
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable

fun TimetableEntity.toDomain(
    timeSlots: List<TimeSlot> = emptyList(),
    courses: List<Course> = emptyList(),
): Timetable = Timetable(
    id = id,
    tableName = tableName,
    startDate = startDate,
    semesterId = semesterId ?: 0L,
    timeSlots = timeSlots,
    courses = courses,
)

fun Timetable.toEntity(): TimetableEntity = TimetableEntity(
    id = id,
    tableName = tableName,
    startDate = startDate,
    semesterId = semesterId.takeIf { it != 0L },
)

fun CourseEntity.toDomain(): Course = Course(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    weekday = weekday,
    startSection = startSection,
    endSection = endSection,
    weeks = weeks,
)

fun Course.toEntity(timetableId: Long): CourseEntity = CourseEntity(
    id = id,
    timetableId = timetableId,
    name = name,
    teacher = teacher,
    location = location,
    weekday = weekday,
    startSection = startSection,
    endSection = endSection,
    weeks = weeks,
)

fun TimeSlotEntity.toDomain(): TimeSlot = TimeSlot(
    number = number,
    startTime = startTime,
    endTime = endTime,
)

fun TimeSlot.toEntity(timetableId: Long): TimeSlotEntity = TimeSlotEntity(
    timetableId = timetableId,
    number = number,
    startTime = startTime,
    endTime = endTime,
)

fun SemesterEntity.toDomain(): Semester = Semester(
    id = id,
    name = name,
    startDate = startDate,
    major = major,
    isActive = isActive,
)

fun Semester.toEntity(): SemesterEntity = SemesterEntity(
    id = id,
    name = name,
    startDate = startDate,
    major = major,
    isActive = isActive,
)
