package com.example.timetable.widget.today

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** 清单注册点：android.appwidget.action.APPWIDGET_UPDATE */
class TodayCoursesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayCoursesWidget()
}
