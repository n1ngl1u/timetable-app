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

    /**
     * 主题切换必须在两帧内生效 —— 这条断言专门用来钉住「不允许再有过渡动画」。
     *
     * 冻结时钟只推进两帧：
     * - 第一帧处理点击事件并把新的 ColorScheme 送进组合；
     * - 第二帧才把新颜色真正画出来（所以不是一帧，一帧时读到的还是旧像素）。
     *
     * 旧实现有 200ms 过渡，两帧约 32ms 只走完全程的六分之一，像素与终点不同，
     * 这条断言在那时会失败；取消过渡后它通过。
     */
    @Test
    fun themeChangeTakesEffectWithinTwoFrames() {
        composeRule.mainClock.autoAdvance = false
        val before = cornerPixel()

        composeRule.onNodeWithText(LIQUID_GLASS).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val after = cornerPixel()
        composeRule.mainClock.autoAdvance = true

        assertNotEquals("切换必须在两帧内完成，不得有过渡动画", before, after)
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
