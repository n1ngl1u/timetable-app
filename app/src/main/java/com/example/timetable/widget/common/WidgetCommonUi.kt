package com.example.timetable.widget.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.timetable.domain.model.WidgetCourseItem

/** Responsive 模式的尺寸桶，LocalSize.current 会命中其中之一；尺寸不写死在布局里。 */
object WidgetSizes {
    val COMPACT = DpSize(110.dp, 110.dp)
    val WIDE = DpSize(250.dp, 110.dp)
    val TALL = DpSize(250.dp, 250.dp)
    val ALL = setOf(COMPACT, WIDE, TALL)
}

/** 颜色全部取自 GlanceTheme.colors，深色模式由 Glance 的 day/night ColorProviders 自动切换。 */
@Composable
fun WidgetSurface(content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        content()
    }
}

@Composable
fun WidgetHeader(title: String, subtitle: String) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = subtitle,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1,
        )
    }
}

@Composable
fun WidgetEmptyText(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
        maxLines = 2,
    )
}

@Composable
fun WidgetVerticalGap(heightDp: Int) {
    Spacer(modifier = GlanceModifier.height(heightDp.dp))
}

@Composable
fun WidgetCourseRow(item: WidgetCourseItem, showLocation: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        Text(
            text = item.timeText,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Text(
            text = item.name,
            style = TextStyle(color = GlanceTheme.colors.onSurface),
            maxLines = 1,
        )
        if (showLocation && item.location.isNotBlank()) {
            Text(
                text = item.location,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 1,
            )
        }
    }
}
