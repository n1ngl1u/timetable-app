package com.example.timetable.data.mapper

import com.example.timetable.data.local.entity.GpaSnapshotEntity
import com.example.timetable.data.local.entity.GradeEntity
import com.example.timetable.domain.model.GpaSummary
import com.example.timetable.domain.model.GradeRecord

fun GradeEntity.toDomain(): GradeRecord = GradeRecord(
    id = id,
    courseName = courseName,
    credit = credit,
    score = score,
    semesterId = semesterId,
    gradePoint = gradePoint,
)

fun GradeRecord.toEntity(): GradeEntity = GradeEntity(
    id = id,
    semesterId = semesterId,
    courseName = courseName,
    credit = credit,
    score = score,
    gradePoint = gradePoint,
)

fun GpaSnapshotEntity.toDomain(): GpaSummary = GpaSummary(
    semesterId = semesterId,
    gpa = gpa,
    totalCredits = totalCredits,
    courseCount = courseCount,
)

fun GpaSummary.toEntity(): GpaSnapshotEntity = GpaSnapshotEntity(
    semesterId = semesterId,
    gpa = gpa,
    totalCredits = totalCredits,
    courseCount = courseCount,
)
