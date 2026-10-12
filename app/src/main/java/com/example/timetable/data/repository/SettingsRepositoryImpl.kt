package com.example.timetable.data.repository

import com.example.timetable.data.local.datastore.SettingsDataStore
import com.example.timetable.domain.model.AppSettings
import com.example.timetable.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = settingsDataStore.settings

    override suspend fun current(): AppSettings = settingsDataStore.current()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        settingsDataStore.update(transform)
    }

    override suspend fun setPureModeEnabled(enabled: Boolean) {
        settingsDataStore.update { it.copy(pureModeEnabled = enabled) }
    }

    override suspend fun setActiveTimetable(timetableId: Long?) {
        settingsDataStore.update { it.copy(activeTimetableId = timetableId) }
    }
}
