package com.example.timetable.ui.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timetable.MainActivity
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val month: YearMonth = YearMonth.now()

    @Before
    fun openCalendarTab() {
        composeRule.onNodeWithContentDescription(CALENDAR_TAB).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun monthGridIsRenderedForCurrentMonth() {
        composeRule.onNodeWithText(monthLabel(month)).assertExists()
        composeRule.onAllNodesWithText(midMonthDay().dayOfMonth.toString())
            .fetchSemanticsNodes()
            .isNotEmpty()
    }

    @Test
    fun clickingDayOpensSheetWithIsoDateTitle() {
        composeRule.onAllNodesWithText(midMonthDay().dayOfMonth.toString())[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(midMonthDay().toString()).assertExists()
    }

    @Test
    fun sheetShowsEmptyStateWhenNoCourse() {
        composeRule.onAllNodesWithText(midMonthDay().dayOfMonth.toString())[0].performClick()
        composeRule.waitForIdle()

        // 无课表数据时 Sheet 必须给出空态文案，而不是空抽屉
        composeRule.onNodeWithText(EMPTY_STATE).assertExists()
    }

    @Test
    fun swipingLeftMovesToNextMonth() {
        composeRule.onNodeWithText(monthLabel(month)).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(monthLabel(month.plusMonths(1))).assertExists()
    }

    private fun midMonthDay(): LocalDate = month.atDay(MID_MONTH_DAY)

    private fun monthLabel(value: YearMonth): String =
        value.year.toString() + " 年 " + value.monthValue + " 月"

    private companion object {
        const val CALENDAR_TAB = "日历"
        const val EMPTY_STATE = "今日无课"
        const val MID_MONTH_DAY = 15
    }
}
