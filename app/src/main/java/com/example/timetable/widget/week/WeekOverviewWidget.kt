package com.example.timetable.widget.week

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.timetable.R
import com.example.timetable.di.WidgetEntryPoint
import com.example.timetable.domain.model.WeekSnapshot
import com.example.timetable.widget.common.WidgetCourseRow
import com.example.timetable.widget.common.WidgetEmptyText
import com.example.timetable.widget.common.WidgetHeader
import com.example.timetable.widget.common.WidgetSizes
import com.example.timetable.widget.common.WidgetSurface
import com.example.timetable.widget.common.WidgetVerticalGap
import dagger.hilt.android.EntryPointAccessors

/**
 * 一周概览小组件（目标 4x4）：只读展示。
 * 窄桶减少展示天数、宽桶每天只保留首门课，不靠压缩字号硬塞。
 */
class WeekOverviewWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .buildWidgetSnapshotUseCase()
            .week()
        val title = context.getString(R.string.widget_week_title)
        val emptyText = context.getString(R.string.widget_week_empty)
        val dayLabels = WEEKDAYS.map { weekday -> context.getString(weekdayLabelRes(weekday)) }
        provideContent {
            GlanceTheme {
                WeekOverviewContent(
                    snapshot = snapshot,
                    title = title,
                    subtitle = subtitleOf(snapshot, context),
                    emptyText = emptyText,
                    dayLabels = dayLabels,
                )
            }
        }
    }

    private fun subtitleOf(snapshot: WeekSnapshot, context: Context): String = when {
        snapshot.tableName.isBlank() -> context.getString(R.string.widget_no_timetable)
        snapshot.weekIndex <= 0 -> snapshot.tableName
        else -> snapshot.tableName + " · " + context.getString(
            R.string.widget_week_format,
            snapshot.weekIndex,
        )
    }

    private fun weekdayLabelRes(weekday: Int): Int = when (weekday) {
        1 -> R.string.widget_weekday_mon
        2 -> R.string.widget_weekday_tue
        3 -> R.string.widget_weekday_wed
        4 -> R.string.widget_weekday_thu
        5 -> R.string.widget_weekday_fri
        6 -> R.string.widget_weekday_sat
        else -> R.string.widget_weekday_sun
    }

    private companion object {
        val WEEKDAYS = (1..7).toList()
    }
}

@Composable
private fun WeekOverviewContent(
    snapshot: WeekSnapshot,
    title: String,
    subtitle: String,
    emptyText: String,
    dayLabels: List<String>,
) {
    val size = LocalSize.current
    val tall = size.height >= WidgetSizes.TALL.height
    val dayCount = if (tall) DAYS_SHOWN_TALL else DAYS_SHOWN_SHORT
    WidgetSurface {
        WidgetHeader(title = title, subtitle = subtitle)
        WidgetVerticalGap(heightDp = 6)
        if (!snapshot.hasCourses) {
            WidgetEmptyText(text = emptyText)
        } else {
            snapshot.days.take(dayCount).forEachIndexed { index, day ->
                Column(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.Top,
                    horizontalAlignment = Alignment.Horizontal.Start,
                ) {
                    Text(
                        text = dayLabels.getOrElse(day.weekday - 1) { EMPTY_LABEL },
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                    )
                    if (day.items.isEmpty()) {
                        Text(
                            text = NO_CLASS_MARK,
                            style = TextStyle(color = GlanceTheme.colors.outline),
                            maxLines = 1,
                        )
                    } else {
                        day.items.take(MAX_ITEMS_PER_DAY).forEach { item ->
                            WidgetCourseRow(item = item, showLocation = false)
                        }
                    }
                    if (index < dayCount - 1) {
                        WidgetVerticalGap(heightDp = 4)
                    }
                }
            }
        }
    }
}

private const val DAYS_SHOWN_TALL = 7
private const val DAYS_SHOWN_SHORT = 3
private const val MAX_ITEMS_PER_DAY = 2
private const val NO_CLASS_MARK = "—"
private const val EMPTY_LABEL = ""
