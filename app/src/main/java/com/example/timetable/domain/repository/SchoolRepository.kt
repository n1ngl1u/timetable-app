package com.example.timetable.domain.repository

import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.ImportResult
import com.example.timetable.domain.importer.SchoolAdapter

interface SchoolRepository {

    fun availableSchools(): List<SchoolAdapter>

    suspend fun importTimetable(request: ImportRequest): ImportResult
}
