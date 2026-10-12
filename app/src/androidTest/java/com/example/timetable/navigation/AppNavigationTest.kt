package com.example.timetable.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timetable.MainActivity
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 三个 tab 互跳。跑的是真实 Hilt 图（app 模块的 androidTest 直接用 TimetableApplication），
 * 因此必须真机/模拟器。
 */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun allThreeDestinationsAreReachable() {
        composeRule.onNodeWithText(TIMETABLE_MARKER).assertExists()

        composeRule.onNodeWithContentDescription(CALENDAR_TAB).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(currentMonthLabel()).assertExists()

        composeRule.onNodeWithContentDescription(SETTINGS_TAB).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(SETTINGS_MARKER).assertExists()

        composeRule.onNodeWithContentDescription(TIMETABLE_TAB).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TIMETABLE_MARKER).assertExists()
    }

    @Test
    fun returningToTimetableKeepsSummaryRow() {
        composeRule.onNodeWithContentDescription(CALENDAR_TAB).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(TIMETABLE_TAB).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(TIMETABLE_MARKER).assertExists()
    }

    private companion object {
        const val TIMETABLE_TAB = "课表"
        const val CALENDAR_TAB = "日历"
        const val SETTINGS_TAB = "设置"
        const val TIMETABLE_MARKER = "课前提醒"
        const val SETTINGS_MARKER = "同步到系统日历"
        fun currentMonthLabel(): String {
            val month = YearMonth.now()
            return month.year.toString() + " 年 " + month.monthValue + " 月"
        }
    }
}
