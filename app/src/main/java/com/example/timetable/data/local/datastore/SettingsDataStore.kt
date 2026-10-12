package com.example.timetable.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timetable.domain.model.AppSettings
import com.example.timetable.domain.model.ReminderRule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "timetable_settings",
)

/**
 * 非敏感设置项持久化；敏感字段一律走 SecurePrefsStore。
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences -> preferences.toAppSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings())
            preferences[Keys.THEME_ID] = updated.themeId
            preferences[Keys.PURE_MODE] = updated.pureModeEnabled
            preferences[Keys.DEFAULT_ADVANCE] = updated.defaultReminderAdvanceMinutes
            preferences[Keys.ACTIVE_SEMESTER] = updated.activeSemesterId
            preferences[Keys.ACTIVE_TIMETABLE] = updated.activeTimetableId
        }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        themeId = this[Keys.THEME_ID] ?: AppSettings.THEME_SYSTEM,
        pureModeEnabled = this[Keys.PURE_MODE] ?: false,
        defaultReminderAdvanceMinutes =
            this[Keys.DEFAULT_ADVANCE] ?: ReminderRule.DEFAULT_ADVANCE_MINUTES,
        activeSemesterId = this[Keys.ACTIVE_SEMESTER] ?: 0L,
        activeTimetableId = this[Keys.ACTIVE_TIMETABLE] ?: 0L,
    )

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val PURE_MODE = booleanPreferencesKey("pure_mode")
        val DEFAULT_ADVANCE = intPreferencesKey("default_reminder_advance_minutes")
        val ACTIVE_SEMESTER = longPreferencesKey("active_semester_id")
        val ACTIVE_TIMETABLE = longPreferencesKey("active_timetable_id")
    }
}
