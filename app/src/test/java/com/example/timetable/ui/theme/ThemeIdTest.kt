package com.example.timetable.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeIdTest {

    @Test
    fun storageKeysAreStableLiterals() {
        assertEquals(
            listOf("default", "time_of_day", "liquid_glass"),
            ThemeId.entries.map { it.storageKey },
        )
    }

    @Test
    fun everyStorageKeyRoundTrips() {
        ThemeId.entries.forEach { id ->
            assertEquals(id, ThemeId.fromStorage(id.storageKey))
        }
    }

    @Test
    fun nullBlankAndUnknownFallBackToDefault() {
        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage(null))
        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage(""))
        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage("   "))
        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage("sepia"))
    }

    /** 首尾空白会被 trim 掉，这是刻意的容错。 */
    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals(ThemeId.LIQUID_GLASS, ThemeId.fromStorage("  liquid_glass  "))
        assertEquals(ThemeId.TIME_OF_DAY, ThemeId.fromStorage("\ttime_of_day\n"))
    }

    /** 骨架期写入过的旧值。 */
    @Test
    fun legacySystemValueFallsBackToDefault() {
        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage("system"))
    }

    /** 大小写敏感：枚举名不该被当作存储值。 */
    @Test
    fun matchingIsCaseSensitive() {
        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage("LIQUID_GLASS"))
        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage("Liquid_Glass"))
        assertEquals(ThemeId.DEFAULT, ThemeId.fromStorage("TIME_OF_DAY"))
    }

    @Test
    fun darkModeStorageKeysAreStableLiterals() {
        assertEquals(
            listOf("follow_system", "light", "dark"),
            DarkModePreference.entries.map { it.storageKey },
        )
    }

    @Test
    fun darkModePreferenceRoundTripsAndFallsBack() {
        DarkModePreference.entries.forEach { preference ->
            assertEquals(preference, DarkModePreference.fromStorage(preference.storageKey))
        }
        assertEquals(DarkModePreference.FOLLOW_SYSTEM, DarkModePreference.fromStorage(null))
        assertEquals(DarkModePreference.FOLLOW_SYSTEM, DarkModePreference.fromStorage("auto"))
        assertEquals(DarkModePreference.FOLLOW_SYSTEM, DarkModePreference.fromStorage("DARK"))
    }
}
