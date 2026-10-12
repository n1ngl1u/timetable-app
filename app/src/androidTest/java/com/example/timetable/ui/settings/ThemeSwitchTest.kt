package com.example.timetable.ui.settings

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timetable.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSwitchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun openSettingsTab() {
        composeRule.onNodeWithContentDescription(SETTINGS_TAB).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun switchingToLiquidGlassRepaintsWithoutRecreatingActivity() {
        val before = cornerPixel()
        val activityBefore = System.identityHashCode(composeRule.activity)

        composeRule.onNodeWithText(LIQUID_GLASS).performClick()
        composeRule.waitForIdle()

        assertNotEquals("切换主题必须改变背景像素", before, cornerPixel())
        assertEquals(
            "切换主题不得重建 Activity",
            activityBefore,
            System.identityHashCode(composeRule.activity),
        )
    }

    @Test
    fun darkModeSegmentedButtonSwitchesImmediately() {
        val before = cornerPixel()

        composeRule.onNodeWithText(DARK).performClick()
        composeRule.waitForIdle()

        assertNotEquals("切到深色必须改变背景像素", before, cornerPixel())
    }

    private fun cornerPixel() = composeRule.onRoot().captureToImage().toPixelMap()[SAMPLE_X, SAMPLE_Y]

    private companion object {
        const val SETTINGS_TAB = "设置"
        const val LIQUID_GLASS = "液态玻璃"
        const val DARK = "深色"
        const val SAMPLE_X = 20
        const val SAMPLE_Y = 20
    }
}
