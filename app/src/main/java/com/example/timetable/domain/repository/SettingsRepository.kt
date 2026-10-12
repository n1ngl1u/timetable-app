package com.example.timetable.domain.repository

import com.example.timetable.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun setPureModeEnabled(enabled: Boolean)

    suspend fun setActiveTimetable(timetableId: Long)
}
