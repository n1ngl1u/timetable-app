package com.example.timetable.core.perf

/**
 * 打点协议常量：日志 tag 与 span 名同时被 App 与 androidTest / Macrobenchmark 读取。
 */
object TraceTags {

    const val LOG_TAG = "TimetablePerf"

    const val APP_ON_CREATE = "app_on_create"
    const val ACTIVITY_ON_CREATE = "activity_on_create"

    const val IMPORT_START = "import_start"

    /** 解析阶段与落库阶段的子 span，把导入总耗时拆成可定位的两段。 */
    const val IMPORT_PARSE = "import_parse"
    const val IMPORT_PERSIST = "import_persist"

    /**
     * 导入耗时 T0 -> T1（A5）：
     * T0 = UI 触发导入；T1 = 课表数据已落库并对 UI 层可观察（Compose 同一数据流即刻重组）。
     */
    const val IMPORT_FIRST_FRAME = "import_first_frame"

    /** T0 标记：数据变更被观察到。 */
    const val WIDGET_DATA_CHANGED = "widget_data_changed"

    /** T0 -> T1 的 span 名：数据变更到两个 widget 的 updateAll() 全部返回。 */
    const val WIDGET_REFRESH = "widget_refresh"

    /** T0 -> T1 口径（A5）：数据变更到 GlanceAppWidget.update() 返回。 */
    const val WIDGET_REFRESH_BUDGET_MS = 500L

    const val IMPORT_BUDGET_MS = 2_000L
    const val COLD_START_BUDGET_MS = 800L
    const val MAX_JANK_RATE_PERCENT = 1.0
}
