package com.example.timetable.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timetable.widget.today.TodayCoursesWidgetReceiver
import com.example.timetable.widget.week.WeekOverviewWidgetReceiver
import org.junit.Assert.assertNotNull
import org.xmlpull.v1.XmlPullParser
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 清单与 appwidget-provider 元数据的静态契约；无需在桌面放置小组件即可运行。 */
@RunWith(AndroidJUnit4::class)
class WidgetProviderDeclarationTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bothReceiversAreRegisteredForAppWidgetUpdate() {
        val names = registeredReceiverNames()

        assertTrue(
            "今日课程 receiver 未注册: " + names,
            names.contains(TodayCoursesWidgetReceiver::class.java.name),
        )
        assertTrue(
            "一周概览 receiver 未注册: " + names,
            names.contains(WeekOverviewWidgetReceiver::class.java.name),
        )
    }

    @Test
    fun todayWidgetDeclaresCellSizeAndDpFallback() {
        val attributes = providerAttributes(TodayCoursesWidgetReceiver::class.java.name)

        assertTrue("缺少 targetCellWidth", attributes["targetCellWidth"] == "2")
        assertTrue("缺少 targetCellHeight", attributes["targetCellHeight"] == "2")
        assertTrue("缺少 minWidth dp 兜底", attributes["minWidth"] == "110dp")
        assertTrue("缺少 minHeight dp 兜底", attributes["minHeight"] == "110dp")
    }

    @Test
    fun weekWidgetDeclaresCellSizeAndDpFallback() {
        val attributes = providerAttributes(WeekOverviewWidgetReceiver::class.java.name)

        assertTrue("缺少 targetCellWidth", attributes["targetCellWidth"] == "4")
        assertTrue("缺少 targetCellHeight", attributes["targetCellHeight"] == "4")
        assertTrue("缺少 minWidth dp 兜底", attributes["minWidth"] == "250dp")
        assertTrue("缺少 minHeight dp 兜底", attributes["minHeight"] == "250dp")
    }

    @Test
    fun bothWidgetsAreResizableAndHaveDescriptionAndInitialLayout() {
        for (receiver in RECEIVERS) {
            val attributes = providerAttributes(receiver)
            assertTrue(receiver + " 不可缩放", attributes["resizeMode"]?.contains("horizontal") == true)
            assertNotNull(receiver + " 缺少 description", attributes["description"])
            assertNotNull(receiver + " 缺少 initialLayout", attributes["initialLayout"])
            assertNotNull(receiver + " 缺少 previewLayout", attributes["previewLayout"])
            assertTrue(
                receiver + " updatePeriodMillis 必须为兜底周期",
                attributes["updatePeriodMillis"] == "1800000",
            )
        }
    }

    private fun registeredReceiverNames(): Set<String> {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName)
        @Suppress("DEPRECATION")
        val receivers = context.packageManager.queryBroadcastReceivers(
            intent,
            PackageManager.GET_META_DATA,
        )
        return receivers.mapNotNull { it.activityInfo?.name }.toSet()
    }

    private fun providerAttributes(receiverName: String): Map<String, String> {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName)
        @Suppress("DEPRECATION")
        val receivers = context.packageManager.queryBroadcastReceivers(
            intent,
            PackageManager.GET_META_DATA,
        )
        val info = receivers.firstOrNull { it.activityInfo?.name == receiverName }?.activityInfo
        assertNotNull(receiverName + " 未在清单注册", info)
        val metaData = info?.metaData
        assertNotNull(receiverName + " 缺少 meta-data", metaData)
        val resId = metaData?.getInt("android.appwidget.provider") ?: 0
        assertTrue(receiverName + " 的 android.appwidget.provider 未指向有效 XML", resId != 0)
        return readAppWidgetProvider(resId)
    }

    private fun readAppWidgetProvider(resId: Int): Map<String, String> {
        val parser: XmlPullParser = context.resources.getXml(resId)
        val attributes = mutableMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                for (index in 0 until parser.attributeCount) {
                    attributes[parser.getAttributeName(index)] = parser.getAttributeValue(index)
                }
                return attributes
            }
            event = parser.next()
        }
        return attributes
    }

    private companion object {
        val RECEIVERS = listOf(
            TodayCoursesWidgetReceiver::class.java.name,
            WeekOverviewWidgetReceiver::class.java.name,
        )
    }
}
