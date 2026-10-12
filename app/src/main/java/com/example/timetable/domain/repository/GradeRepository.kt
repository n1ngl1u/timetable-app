package com.example.timetable.domain.repository

import com.example.timetable.domain.model.GradeRecord
import kotlinx.coroutines.flow.Flow

interface GradeRepository {

    fun observeGrades(semesterId: Long): Flow<List<GradeRecord>>

    suspend fun upsertAll(records: List<GradeRecord>)

    suspend fun deleteBySemester(semesterId: Long)
}
