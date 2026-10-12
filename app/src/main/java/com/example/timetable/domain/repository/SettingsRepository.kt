package com.example.timetable.domain.repository

import com.example.timetable.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun setPureModeEnabled(enabled: Boolean)

    /** null 表示清空活跃课表。 */
    suspend fun setActiveTimetable(timetableId: Long?)
}
