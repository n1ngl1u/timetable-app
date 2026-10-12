package com.example.timetable.data.local.datastore

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timetable.domain.model.AppSettings
import com.example.timetable.ui.theme.DarkModePreference
import com.example.timetable.ui.theme.ThemeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsThemePersistenceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val store: SettingsDataStore get() = SettingsDataStore(context)

    @After
    fun restoreDefaults() = runBlocking {
        store.update {
            it.copy(
                themeId = AppSettings.DEFAULT_THEME_ID,
                darkModePreference = AppSettings.DEFAULT_DARK_MODE_PREFERENCE,
            )
        }
    }

    @Test
    fun themeAndDarkModeRoundTripThroughRealDataStore() = runBlocking {
        store.update {
            it.copy(
                themeId = ThemeId.LIQUID_GLASS.storageKey,
                darkModePreference = DarkModePreference.DARK.storageKey,
            )
        }

        val read = store.current()

        assertEquals(ThemeId.LIQUID_GLASS.storageKey, read.themeId)
        assertEquals(DarkModePreference.DARK.storageKey, read.darkModePreference)
        assertEquals(ThemeId.LIQUID_GLASS, ThemeId.fromStorage(read.themeId))
        assertEquals(DarkModePreference.DARK, DarkModePreference.fromStorage(read.darkModePreference))
    }

    @Test
    fun illegalStoredValuesResolveToFallbacks() = runBlocking {
        store.update {
            it.copy(themeId = ILLEGAL_THEME, darkModePreference = ILLEGAL_DARK_MODE)
        }

        val read = store.current()

        assertEquals(ILLEGAL_THEME, read.themeId)
        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage(read.themeId))
        assertEquals(DarkModePreference.FOLLOW_SYSTEM, DarkModePreference.fromStorage(read.darkModePreference))
    }

    @Test
    fun legacySystemThemeValueResolvesToDefault() = runBlocking {
        store.update { it.copy(themeId = AppSettings.LEGACY_THEME_SYSTEM) }

        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage(store.current().themeId))
    }

    private companion object {
        const val ILLEGAL_THEME = "not_a_theme"
        const val ILLEGAL_DARK_MODE = "??"
    }
}
