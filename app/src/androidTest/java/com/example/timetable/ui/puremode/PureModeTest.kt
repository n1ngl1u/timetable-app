package com.example.timetable.ui.puremode

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timetable.MainActivity
import com.example.timetable.data.local.datastore.SettingsDataStore
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.isToggleable
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PureModeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val store: SettingsDataStore
        get() = SettingsDataStore(composeRule.activity.applicationContext)

    @Before
    fun resetPureMode() = runBlocking {
        store.update { it.copy(pureModeEnabled = false, pureModeHintShown = false) }
        composeRule.waitForIdle()
    }

    @After
    fun restorePureMode() = runBlocking {
        store.update { it.copy(pureModeEnabled = false) }
    }

    @Test
    fun pureModeHidesNavigationBar() {
        assertTrue("前置条件：导航栏可见", navBarVisible())

        enablePureMode()

        assertFalse("进入纯净模式后不得渲染导航栏", navBarVisible())
    }

    @Test
    fun doubleBackExitsPureMode() {
        enablePureMode()
        assertFalse(navBarVisible())

        pressBack()
        assertFalse("第一次返回只提示，不能退出", navBarVisible())

        pressBack()
        composeRule.waitForIdle()
        assertTrue("第二次返回应退出纯净模式并恢复导航栏", navBarVisible())
    }

    @Test
    fun systemBarsAreHiddenAndRestored() {
        enablePureMode()
        composeRule.waitUntil(TIMEOUT_MILLIS) { !systemBarsVisible() }

        pressBack()
        pressBack()
        composeRule.waitUntil(TIMEOUT_MILLIS) { systemBarsVisible() }
    }

    @Test
    fun pureModeSurvivesActivityRecreation() {
        enablePureMode()
        assertFalse(navBarVisible())

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        assertFalse("进程重建后开关仍为 true，导航栏必须仍然不渲染", navBarVisible())
        composeRule.waitUntil(TIMEOUT_MILLIS) { !systemBarsVisible() }
    }

    /**
     * 纯净模式关闭时，外壳不注册 BackHandler，底部抽屉的返回关闭必须照旧。
     * 「纯净模式开启 + 抽屉打开」的组合在 UI 上不可达：打开纯净模式会直达课表页并隐藏导航栏，
     * 用户没有任何入口回到日历页，因此只能覆盖到这个方向。
     */
    @Test
    fun calendarSheetBackDismissStillWorksWhenPureModeIsOff() {
        composeRule.onNodeWithContentDescription(CALENDAR_TAB).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(MID_MONTH_DAY.toString())[0].performClick()
        composeRule.waitForIdle()

        val sheetTitle = LocalDate.now().withDayOfMonth(MID_MONTH_DAY).toString()
        composeRule.onNodeWithText(sheetTitle).assertExists()

        pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(sheetTitle).assertDoesNotExist()
        assertTrue("返回只应关掉抽屉，不得影响导航栏", navBarVisible())
    }

    private fun enablePureMode() {
        composeRule.onNodeWithContentDescription(SETTINGS_TAB).performClick()
        composeRule.waitForIdle()
        // 设置页第一个可切换控件就是纯净模式开关
        composeRule.onAllNodes(isToggleable())[0].performClick()
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun navBarVisible(): Boolean =
        composeRule.onAllNodesWithText(CALENDAR_TAB).fetchSemanticsNodes().isNotEmpty()

    private fun systemBarsVisible(): Boolean =
        ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.systemBars())
            ?: true

    private companion object {
        const val SETTINGS_TAB = "设置"
        const val CALENDAR_TAB = "日历"
        const val MID_MONTH_DAY = 15
        const val TIMEOUT_MILLIS = 5_000L
    }
}
