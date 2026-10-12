# 真机验证清单

本机无 Android 设备与模拟器，下列项目**只完成编译验证**，需接入真机后逐条执行。
在跑完并记录实测数值之前，任何指标都不得声称达标。

设备要求：荣耀 500 Pro（MagicOS 11 / Android 17）。
执行方式：`./gradlew :app:connectedDebugAndroidTest` 与 `./gradlew :benchmark:connectedBenchmarkAndroidTest`。

| # | 验证项 | 关联测试 / 命令 | 前置条件 | 状态 |
|---|---|---|---|---|
| 1 | SQL 事务：导入到已有课表为整表替换 | TimetableRepositoryReplaceTest（待补，androidTest） | 无 | 未执行 |
| 2 | 冷启动 ≤ 800ms | `benchmark/.../StartupBenchmark`（StartupTimingMetric） | 关闭省电策略，真机 | 未执行 |
| 3 | 滚动 Jank < 1% | `benchmark/.../ScrollJankBenchmark`（FrameTimingMetric + JankStats 断言） | 关闭省电策略，真机 | 未执行 |
| 4 | 课表导入 ≤ 2s | `benchmark/.../ImportBenchmark`（T0 触发 → import_first_frame span） | 真机 | 未执行 |
| 5 | 小组件刷新 ≤ 500ms | `app/src/androidTest/.../widget/WidgetRefreshTimingTest` | 桌面已放置「今日课程」与「一周概览」；测试会先断言实例存在，否则直接失败 | 未执行 |
| 6 | Room MIGRATION_1_2（v1→v2，reminders 加 weeks 列） | `app/src/androidTest/.../data/local/db/Migration1To2Test` | 无 | 未执行 |
| 7 | 精确闹钟真实触发 | 手工：开启提醒 → 观察到点是否弹出通知 | 已授予 SCHEDULE_EXACT_ALARM | 未执行 |
| 8 | 降级路径 WorkManager 实际执行 | 手工：撤销精确闹钟权限 → 观察提醒仍会到达 | 撤销授权；执行时机受 Doze 影响，**±15min 级退化，不保证具体时刻** | 未执行 |
| 9 | 通知实际弹出 + 点击打开 MainActivity | 手工：点击通知，确认落到 MainActivity | POST_NOTIFICATIONS 已授予 | 未执行 |
| 10 | POST_NOTIFICATIONS 被拒时只记录日志不弹通知 | 手工：撤销通知权限 → 确认 logcat 出现「通知被禁用，提醒仅记录」 | 撤销授权 | 未执行 |
| 11 | 精确闹钟授权/撤销后自动重排 | 手工：系统设置里切换授权 → 确认重新调度 | 无 | 未执行 |
| 12 | BOOT_COMPLETED / TIMEZONE_CHANGED / TIME_SET 后重排 | 手工：重启设备 / 改时区 / 改系统时间 | 无 | 未执行 |
| 13 | 通知渠道创建幂等 | `app/src/androidTest/.../reminder/ReminderNotificationChannelTest` | 无 | 未执行 |
| 14 | PendingIntent 构造与不可变标志 | `app/src/androidTest/.../reminder/ReminderPendingIntentTest` | 无 | 未执行 |
| 15 | 真实创建「课程表」专用日历（设备默认账号） | `app/src/androidTest/.../calendar/CalendarContractSyncTest#courseCalendarIsCreatedAndReused` | 授予 READ/WRITE_CALENDAR | 未执行 |
| 16 | 事件插入后按 CALENDAR_ID 查询可见，删除后消失 | `.../CalendarContractSyncTest#insertedEventIsQueryableInOurCalendar` | 同上 | 未执行 |
| 17 | 日历权限**拒绝**路径的真实行为 | 手工：系统设置撤销日历权限 → 触发同步 → 确认返回 `Skipped(PERMISSION_DENIED)` 且不崩溃、不抛 SecurityException | 撤销授权；**无法自动化**（`pm revoke` 会杀掉被测进程） | 未执行 |
| 18 | Room MIGRATION_2_3（v2→v3，新建 calendar_event_links + 两个索引） | `app/src/androidTest/.../data/local/db/Migration2To3Test` | 无 | 未执行 |
| 19 | 增量同步真机表现：改时间/地点/周次后事件随之更新而非重建；重导入整表后孤儿事件被 `pruneOrphanEvents` 清掉 | 手工：改课程 → 看系统日历；重新导入 → 看事件数不翻倍 | 授予日历权限；**RRULE 在系统日历 App 里的展开效果需肉眼确认** | 未执行 |
| 21 | 导航切换：三个 tab 互跳 | `app/src/androidTest/.../navigation/AppNavigationTest` | 真机/模拟器 | 未执行 |
| 22 | 月视图点击某天弹出单日详情 Sheet | `app/src/androidTest/.../ui/calendar/CalendarScreenTest#clickingDayOpensSheetWithDateTitle` | 真机/模拟器 | 未执行 |
| 23 | 单日详情 Sheet 内容正确（有课/空态）与左滑切月 | `.../CalendarScreenTest` | 真机/模拟器 | 未执行 |
| 24 | 删除课程后系统日历事件与 link 记录都消失 | `app/src/androidTest/.../calendar/CalendarRepositoryDeleteEventsTest` | 授予 READ/WRITE_CALENDAR | 未执行 |
| 20 | `FakeCalendarRepository` 的局限 | 说明项：该替身**不模拟 ContentResolver**，不产生真实日历事件，`applyChanges` 只记录计划；系统日历真实行为全部依赖第 15–19 项 | 无 | 已知边界 |

## 孤儿事件清理指引

`CalendarSyncPlan` 的写入失败走**补偿式回滚**（逐条删除本次新建事件，单条失败不阻断其余并把
event id 打进日志 `TimetableCalendar`）。若补偿也失败，会残留孤儿事件，处理顺序：

1. 打开系统日历，找到「课程表」这个日历，手工删除残留事件；
2. 或调用 `CalendarRepository.clearSyncedEvents()` 全量清空该专用日历并重置 link 表；
3. 再重新触发 `SyncToCalendarUseCase` 全量重建。

课程被删除时**必须**先调 `CalendarRepository.deleteEventsForCourse(courseId)` 再删课程：
link 表外键是 `ON DELETE CASCADE`，先删课程会让 link 先消失，系统日历里的事件就再也找不到归属。
当前没有课程删除的 UI 入口，该接口与单测已就绪，接入删除 UI 时必须走这条路。
