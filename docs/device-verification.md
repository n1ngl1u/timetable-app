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
| 7 | 精确闹钟真实触发 | 手工：开启提醒 → 观察到点是否弹出通知；`AlarmManager.setExactAndAllowWhileIdle` | 已授予 SCHEDULE_EXACT_ALARM | 未执行 |
| 8 | 降级路径 WorkManager 实际执行 | 手工：撤销精确闹钟权限 → 观察提醒仍会到达 | 撤销授权；执行时机受 Doze 影响，**±15min 级退化，不保证具体时刻** | 未执行 |
| 9 | 通知实际弹出 + 点击打开 MainActivity | 手工：点击通知，确认落到 MainActivity | POST_NOTIFICATIONS 已授予 | 未执行 |
| 10 | POST_NOTIFICATIONS 被拒时只记录日志不弹通知 | 手工：撤销通知权限 → 确认 logcat 出现「通知被禁用，提醒仅记录」 | 撤销授权 | 未执行 |
| 11 | 精确闹钟授权/撤销后自动重排 | 手工：系统设置里切换授权 → 确认重新调度（logcat + 通知行为） | 无 | 未执行 |
| 12 | BOOT_COMPLETED / TIMEZONE_CHANGED / TIME_SET 后重排 | 手工：重启设备 / 改时区 / 改系统时间 | 无 | 未执行 |
| 13 | 通知渠道创建幂等 | `app/src/androidTest/.../reminder/ReminderNotificationChannelTest` | 无 | 未执行 |
| 14 | PendingIntent 构造与不可变标志 | `app/src/androidTest/.../reminder/ReminderPendingIntentTest` | 无 | 未执行 |

## 已知未覆盖的路径（非设备依赖，属测试替身局限）

- `FakeReminderRepository.enabledSchedules()` / `scheduleFor()` 返回空值：调度投影是
  `reminders ⋈ courses ⋈ timetables ⟕ time_slots` 的 Room JOIN 语义，测试替身不持有课程与课表数据，
  无法如实重建。该 JOIN 路径由第 6/7/8 项在设备侧覆盖。
