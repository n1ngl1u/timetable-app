package com.example.timetable.ui.timetable

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timetable.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 导入入口的端到端路径：TopAppBar 图标 -> 面板 -> 手动粘贴 -> 导入。
 * JSON 用硬编码样例，不现场拼，避免测出的是自己写的东西。
 */
@RunWith(AndroidJUnit4::class)
class ImportSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun importSheetOpensFromTopBarAndImportsPastedJson() {
        composeRule.onNodeWithContentDescription(IMPORT_ENTRY).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(SHEET_TITLE).assertExists()
        composeRule.onNodeWithText(FROM_CLIPBOARD).assertExists()
        composeRule.onNode(hasSetTextAction()).performTextInput(VALID_PAYLOAD)
        composeRule.onNodeWithText(SUBMIT).performClick()
        composeRule.waitForIdle()

        confirmOverwriteIfAsked()

        composeRule.onNodeWithText(SUCCESS_MESSAGE).assertExists()
    }

    @Test
    fun missingFieldIsReportedWithItsPath() {
        composeRule.onNodeWithContentDescription(IMPORT_ENTRY).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).performTextInput(MISSING_NAME_PAYLOAD)
        composeRule.onNodeWithText(SUBMIT).performClick()
        composeRule.waitForIdle()

        confirmOverwriteIfAsked()

        composeRule.onNodeWithText(MISSING_FIELD_MESSAGE, substring = true).assertExists()
    }

    /** 已有活跃课表时会先弹覆盖确认；没有则直接导入。两条路径都要走得通。 */
    private fun confirmOverwriteIfAsked() {
        val nodes = composeRule.onAllNodesWithText(OVERWRITE_CONFIRM).fetchSemanticsNodes()
        if (nodes.isNotEmpty()) {
            composeRule.onNodeWithText(OVERWRITE_CONFIRM).performClick()
            composeRule.waitForIdle()
        }
    }

    private companion object {
        const val IMPORT_ENTRY = "导入课表"
        const val SHEET_TITLE = "导入课表"
        const val FROM_CLIPBOARD = "从剪贴板读取"
        const val SUBMIT = "导入"
        const val OVERWRITE_CONFIRM = "覆盖导入"
        const val SUCCESS_MESSAGE = "导入成功：2 门课程"
        const val MISSING_FIELD_MESSAGE = "字段缺失: courses[0].name"

        const val VALID_PAYLOAD =
            "{\"tableName\":\"测试课表\",\"startDate\":\"2026-09-07\"," +
                "\"timeSlots\":[{\"number\":1,\"startTime\":\"08:00\",\"endTime\":\"08:45\"}]," +
                "\"courses\":[" +
                "{\"name\":\"数据结构\",\"location\":\"计算机楼 402\",\"weekday\":1,\"startSection\":1,\"endSection\":2,\"weeks\":[1,2]}," +
                "{\"name\":\"高等数学\",\"location\":\"教一 101\",\"weekday\":3,\"startSection\":1,\"endSection\":2,\"weeks\":[1,2]}]}"

        const val MISSING_NAME_PAYLOAD =
            "{\"courses\":[{\"weekday\":1,\"startSection\":1,\"endSection\":2,\"weeks\":[1]}]}"
    }
}
