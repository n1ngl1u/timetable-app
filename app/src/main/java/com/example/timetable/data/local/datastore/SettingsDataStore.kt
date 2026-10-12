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
            preferences[Keys.DARK_MODE_PREFERENCE] = updated.darkModePreference
            preferences[Keys.PURE_MODE] = updated.pureModeEnabled
            preferences[Keys.PURE_MODE_HINT_SHOWN] = updated.pureModeHintShown
            preferences[Keys.REMINDERS_ENABLED] = updated.remindersEnabled
            preferences[Keys.CALENDAR_SYNC_ENABLED] = updated.calendarSyncEnabled
            preferences[Keys.DEFAULT_ADVANCE] = updated.defaultReminderAdvanceMinutes
            preferences[Keys.ACTIVE_SEMESTER] = updated.activeSemesterId
            val activeTimetableId = updated.activeTimetableId
            if (activeTimetableId != null) {
                preferences[Keys.ACTIVE_TIMETABLE] = activeTimetableId
            } else {
                // null 语义用「键不存在」表达，而不是写入哨兵值
                preferences.remove(Keys.ACTIVE_TIMETABLE)
            }
        }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        themeId = this[Keys.THEME_ID] ?: AppSettings.DEFAULT_THEME_ID,
        darkModePreference = this[Keys.DARK_MODE_PREFERENCE]
            ?: AppSettings.DEFAULT_DARK_MODE_PREFERENCE,
        pureModeEnabled = this[Keys.PURE_MODE] ?: false,
        pureModeHintShown = this[Keys.PURE_MODE_HINT_SHOWN] ?: false,
        remindersEnabled = this[Keys.REMINDERS_ENABLED] ?: false,
        calendarSyncEnabled = this[Keys.CALENDAR_SYNC_ENABLED] ?: false,
        defaultReminderAdvanceMinutes =
            this[Keys.DEFAULT_ADVANCE] ?: ReminderRule.DEFAULT_ADVANCE_MINUTES,
        activeSemesterId = this[Keys.ACTIVE_SEMESTER] ?: 0L,
        // 骨架期可能写入过哨兵 0，只在读取侧归一为 null，不回写：
        // 回写会在 Flow 上反复发射同一个值，导致下游无谓刷新。
        activeTimetableId = this[Keys.ACTIVE_TIMETABLE]?.takeIf { it != 0L },
    )

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val DARK_MODE_PREFERENCE = stringPreferencesKey("dark_mode_preference")
        val PURE_MODE = booleanPreferencesKey("pure_mode")
        val PURE_MODE_HINT_SHOWN = booleanPreferencesKey("pure_mode_hint_shown")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val CALENDAR_SYNC_ENABLED = booleanPreferencesKey("calendar_sync_enabled")
        val DEFAULT_ADVANCE = intPreferencesKey("default_reminder_advance_minutes")
        val ACTIVE_SEMESTER = longPreferencesKey("active_semester_id")
        val ACTIVE_TIMETABLE = longPreferencesKey("active_timetable_id")
    }
}
