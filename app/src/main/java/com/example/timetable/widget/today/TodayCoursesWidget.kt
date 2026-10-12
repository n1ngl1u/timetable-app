package com.example.timetable.widget.today

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import com.example.timetable.R
import com.example.timetable.di.WidgetEntryPoint
import com.example.timetable.domain.model.TodaySnapshot
import com.example.timetable.widget.common.WidgetCourseRow
import com.example.timetable.widget.common.WidgetEmptyText
import com.example.timetable.widget.common.WidgetHeader
import com.example.timetable.widget.common.WidgetSizes
import com.example.timetable.widget.common.WidgetSurface
import com.example.timetable.widget.common.WidgetVerticalGap
import dagger.hilt.android.EntryPointAccessors

/**
 * 今日课程小组件：只读展示，无任何交互按钮。
 * 尺寸走 SizeMode.Responsive，LocalSize 命中宽桶时补上教室并降低条目上限。
 */
class TodayCoursesWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .buildWidgetSnapshotUseCase()
            .today()
        val title = context.getString(R.string.widget_today_title)
        val emptyText = context.getString(R.string.widget_today_empty)
        provideContent {
            GlanceTheme {
                TodayCoursesContent(
                    snapshot = snapshot,
                    title = title,
                    subtitle = subtitleOf(snapshot, context),
                    emptyText = emptyText,
                )
            }
        }
    }

    private fun subtitleOf(snapshot: TodaySnapshot, context: Context): String = when {
        snapshot.tableName.isBlank() -> context.getString(R.string.widget_no_timetable)
        snapshot.weekIndex <= 0 -> snapshot.tableName
        else -> snapshot.tableName + " · " + context.getString(
            R.string.widget_week_format,
            snapshot.weekIndex,
        )
    }
}

@Composable
private fun TodayCoursesContent(
    snapshot: TodaySnapshot,
    title: String,
    subtitle: String,
    emptyText: String,
) {
    val size = LocalSize.current
    val wide = size.width >= WidgetSizes.WIDE.width
    WidgetSurface {
        WidgetHeader(title = title, subtitle = subtitle)
        WidgetVerticalGap(heightDp = 6)
        if (!snapshot.hasCourses) {
            WidgetEmptyText(text = emptyText)
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                snapshot.items
                    .take(if (wide) MAX_WIDE_ITEMS else MAX_COMPACT_ITEMS)
                    .forEach { item -> WidgetCourseRow(item = item, showLocation = wide) }
            }
        }
    }
}

private const val MAX_WIDE_ITEMS = 3
private const val MAX_COMPACT_ITEMS = 4
