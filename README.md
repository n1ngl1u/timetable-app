# 课程表 TimetableApp

## 这是什么

一个 Android 原生课程表应用。导入教务课表之后，它负责回答两个问题：今天上什么、在哪上，以及什么时候该出发。

面向在校学生：需要在上课日快速看到当天课程，并且希望提醒与系统日历都不依赖第三方服务。

应用不联网也能使用：导入支持本地文件与剪贴板，数据全部落在本地加密数据库中。

## 功能

- 教务导入：适配器注册表 + 通用 JSON 解析，当前提供本地文件与剪贴板两种载荷源
- 多课表：多份课表共存，可切换活跃课表
- 桌面小组件：「今日课程」与「一周概览」，Glance 实现
- 课前提醒：精确闹钟优先，无权限时降级到 WorkManager
- 系统日历同步：单向写入系统日历中的专用「课程表」日历，按内容哈希做增量更新
- 月视图日历：手写月网格、左右滑动切月、单日详情底部抽屉
- 三套主题：默认（Material You 动态取色）、时间动态（四时段渐变）、液态玻璃（分层背景 + RenderEffect 模糊）
- 纯净模式：隐藏导航栏与系统栏，双击返回退出
- 凭据加固：SQLCipher 整库加密 + Keystore 字段级加密 + AAD 记录级绑定

## 技术栈

版本号取自 [gradle/libs.versions.toml](gradle/libs.versions.toml)，每一项都带有官方发布页链接。下表两项例外，已在「来源」列注明。

| 分类 | 组件 | 版本 | 来源 |
|---|---|---|---|
| 语言与构建 | Kotlin | 2.3.20 | libs.versions.toml |
| 语言与构建 | Android Gradle Plugin | 9.4.1 | libs.versions.toml |
| 语言与构建 | Gradle | 9.6.0 | gradle/wrapper/gradle-wrapper.properties |
| 语言与构建 | KSP | 2.3.12 | libs.versions.toml |
| UI | Compose BOM | 2026.09.00 | libs.versions.toml |
| UI | Material3 | 1.4.0 | 由 Compose BOM 解析，声明处不带版本 |
| UI | Navigation Compose | 2.10.2 | libs.versions.toml |
| 架构 | Hilt | 2.60.1 | libs.versions.toml |
| 架构 | Coroutines / Flow | 1.11.0 | libs.versions.toml |
| 数据 | Room | 2.8.5 | libs.versions.toml |
| 数据 | DataStore | 1.2.1 | libs.versions.toml |
| 数据 | SQLCipher | 4.19.1 | libs.versions.toml |
| 小组件 | Glance | 1.2.0 | libs.versions.toml |
| 后台任务 | WorkManager | 2.12.0 | libs.versions.toml |
| 网络 | OkHttp | 5.5.0 | libs.versions.toml |
| 网络 | Retrofit | 3.0.0 | libs.versions.toml |
| 网络 | kotlinx-serialization | 1.11.0 | libs.versions.toml |
| 测试 | JUnit 4 | 4.13.2 | libs.versions.toml |
| 测试 | androidx.test.ext:junit | 1.3.0 | libs.versions.toml |
| 测试 | Espresso | 3.7.0 | libs.versions.toml |
| 测试 | UiAutomator | 2.4.0 | libs.versions.toml |
| 测试 | Macrobenchmark | 1.5.0 | libs.versions.toml |
| 测试 | BaselineProfile | 1.5.0 | libs.versions.toml |

## 架构

```text
com.example.timetable
├── core/                 通用层：UI 基类、性能打点、时间源，零业务依赖
│   ├── common/           AppResult / AppError / DispatcherProvider
│   ├── perf/             PerfTracer / JankStatsTracker / TraceTags
│   ├── time/             TimeProvider（可注入时钟）
│   └── ui/               BaseViewModel / UiState / UiEvent
├── domain/               纯 Kotlin 层：模型、用例、仓库接口，禁止 Android 依赖
│   ├── model/            Course / Timetable / Semester / ReminderRule / WidgetSnapshot
│   ├── calendar/         事件规格、连续周次拆分、增量 diff
│   ├── importer/         导入契约、适配器注册表、解析器接口
│   ├── reminder/         提醒调度策略与触发时刻计算（纯函数）
│   ├── repository/       六个仓库接口
│   └── usecase/          导入、观察活跃课表、同步提醒规则、同步日历、删除课程
├── data/                 实现层：Room / SQLCipher / Keystore / 适配器 / 仓库实现
│   ├── local/            entity、dao、db（含迁移）、datastore、converter
│   ├── crypto/           主密钥、字段级加解密、密文信封、SecurePrefsStore
│   ├── calendar/         CalendarContract 读写与权限判定
│   ├── reminder/         通知、调度器、闹钟接收器、WorkManager 降级
│   ├── adapter/          通用 JSON 解析、本地文件与剪贴板载荷源
│   ├── mapper/           实体与领域模型互转
│   └── repository/       仓库实现
├── di/                   Hilt 模块与 @EntryPoint
├── ui/                   界面层，按页面分包
│   ├── timetable/        课表页：摘要行、提醒行、课程列表
│   ├── calendar/         月视图与单日详情
│   ├── settings/         设置页、主题选择、纯净模式开关
│   ├── theme/            ThemeTokens、三套主题、背景层、主题宿主
│   └── puremode/         纯净模式状态与双击返回判定
├── widget/               Glance 小组件：today / week / common
└── navigation/           类型安全路由与应用外壳
```

依赖方向是 `ui → domain ← data`：界面层只认 domain 的模型与用例，实现层也只实现 domain 的接口，两侧互不认识。`di` 负责把实现绑定到接口，是唯一同时认识两边的包。

`domain` 与 `core` 不依赖任何 Android API，因此可以整体跑 JVM 单元测试；这也是通知内容、提醒触发时刻、月网格、日历增量 diff 这些逻辑被刻意放在那里的原因。

## 项目结构

```text
.
├── .github/workflows/build.yml   CI：push 到 main 触发 assembleDebug，产物 app-debug-apk
├── app/                          主模块，唯一 APK 来源
│   ├── src/main/java/            应用代码，按上面的包结构组织
│   ├── src/test/                 JVM 单元测试
│   ├── src/androidTest/          instrumentation 测试
│   ├── schemas/                  Room 导出的 schema（2.json / 3.json），随代码提交
│   └── src/main/res/             本地矢量图标、字符串、小组件描述
├── benchmark/                    Macrobenchmark：冷启动、滚动 Jank、导入耗时
├── baselineprofile/              BaselineProfile 生成器
├── docs/conventions.md           项目硬规则 12 条
├── docs/device-verification.md   真机验证清单 35 条
├── gradle/libs.versions.toml     唯一版本源
├── build.gradle.kts              根构建脚本
├── settings.gradle.kts           三个模块的 include
├── gradle.properties
└── gradlew / gradlew.bat
```

## 构建

### 方式一：GitHub Actions（推荐，零本地环境）

Fork 或 clone 后推到自己的 GitHub 仓库，push 到 `main` 会自动触发构建：

```bash
git clone https://github.com/<上游>/timetable-app.git
cd timetable-app
git remote set-url origin https://github.com/<你的账号>/timetable-app.git
git push origin main
```

在仓库的 Actions 页面可以看到构建过程，成功后进入该次运行，在 Artifacts 区下载 `app-debug-apk`（保留 30 天）。工作流也支持在 Actions 页面手动触发。

这条路径不需要本地装 JDK、Android SDK 或 Gradle：工作流自带 temurin JDK 17 与 Gradle 缓存。

### 方式二：本地构建

```bash
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

前置条件：JDK 17+ 与 Android SDK（compileSdk 37）。Debug 包同时启用 V1 与 V2 签名，可以直接侧载安装。

## 测试策略

**JVM 单元测试：207 个，分布在 25 个测试类**，全部可在本地跑完，不依赖设备：

```bash
./gradlew :app:testDebugUnitTest
```

覆盖面分三类：

- `domain` 纯逻辑：导入结果契约、提醒触发时刻与调度策略、日历连续周次拆分与增量 diff、月网格生成、主题时段判定
- `data` 层编排（用替身，不碰真实 Keystore 与 SharedPreferences）：密文信封格式与错误映射、StrongBox 回落分支、SecurePrefsStore 的旧格式迁移与清空
- `ui` ViewModel 状态机：日历月视图状态转换、纯净模式双击判定、主题 token 完整性

**Android instrumentation 测试：15 个测试文件，已编写但尚未执行**（开发机无设备与模拟器）。它们覆盖导航、月视图交互、主题切换、纯净模式、系统日历读写、Keystore 加密、数据库迁移等需要真实系统能力的行为，逐条登记在 [docs/device-verification.md](docs/device-verification.md) 的 35 条清单里。

**性能指标：测试代码已就位，需真机执行。**

| 指标 | 目标 | 位置 |
|---|---|---|
| 冷启动 | ≤ 800ms | `benchmark/StartupBenchmark` |
| 课表导入 | ≤ 2s | `benchmark/ImportBenchmark` |
| 滚动 Jank | < 1% | `benchmark/ScrollJankBenchmark` |
| 小组件刷新 | ≤ 500ms | `app/src/androidTest/.../WidgetRefreshTimingTest` |

以上性能数值尚未在真机测量，不作为已达标声明。

## 项目约定

动手改代码前请先读 [docs/conventions.md](docs/conventions.md)。那里记录了 12 条硬规则，每条都附上「违反了会发生什么」：图标一律用本地矢量 XML（Material3 1.4.0 不再传递 material-icons-core）、时间与间隔必须走 `TimeProvider`、敏感字段加密必须绑定 AAD 上下文、Room 升级必须配真实迁移、课程删除只能走 `DeleteCourseUseCase` 等。

这些规则不是风格偏好，每一条背后都有一次真实的构建失败或数据损坏事故。

## 已知限制

- **成绩与 GPA 未实现**：实体、DAO 与仓储实现已在数据层就位，但没有对应的 UI 与用例，功能不可用
- **教务导入只覆盖载荷本身**：目前提供本地文件与剪贴板两种载荷源，真实教务系统的网络适配器尚未实现
- **性能指标未在真机测量**：四个指标只有测试代码，没有任何实测数值
- **Release 未产出**：Release 已配置 V1 + V2 签名，但签名材料来自仓库根目录的 `keystore.properties`（未入库）；该文件缺失时 Release 回落到 Debug 签名。CI 只构建 Debug APK
- **instrumentation 测试未执行**：15 个测试文件需要真机或模拟器

## 许可证

未指定许可证。仓库根目录没有 LICENSE 文件，因此默认保留所有权利。如需在其它项目中复用本仓库代码，请先与仓库所有者确认。
