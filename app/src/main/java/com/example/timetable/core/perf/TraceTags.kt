package com.example.timetable.core.perf

/**
 * 打点协议常量：日志 tag 与 span 名同时被 App 与 androidTest / Macrobenchmark 读取。
 */
object TraceTags {

    const val LOG_TAG = "TimetablePerf"

    const val APP_ON_CREATE = "app_on_create"
    const val ACTIVITY_ON_CREATE = "activity_on_create"

    const val IMPORT_START = "import_start"
    const val IMPORT_FIRST_FRAME = "import_first_frame"

    const val WIDGET_DATA_CHANGED = "widget_data_changed"
    const val WIDGET_UPDATE_RETURNED = "widget_update_returned"

    /** T0 -> T1 口径（A5）：数据变更到 GlanceAppWidget.update() 返回。 */
    const val WIDGET_REFRESH_BUDGET_MS = 500L

    const val IMPORT_BUDGET_MS = 2_000L
    const val COLD_START_BUDGET_MS = 800L
    const val MAX_JANK_RATE_PERCENT = 1.0
}
