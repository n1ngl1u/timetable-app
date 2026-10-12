# 项目约定

本文件记录**已经被真实故障或真实构建失败验证过**的硬规则。每条给出背景原因与硬规则两行：
背景原因说明「违反了会发生什么」，硬规则是可直接照做的判定条件。

新增条目要求：必须有具体事故或官方文档支撑，不接受「一般来说更好」这类偏好。

---

### 1. 图标一律使用本地矢量 XML

**背景原因**：Material3 1.4.0 起不再传递依赖 `material-icons-core`，代码里写 `Icons.Filled.*` 会在编译期直接 `Unresolved reference`（模块 5B 实际发生，浪费一轮本地构建）；而补上 `material-icons-extended` 会给 Debug APK 增加数 MB，其中绝大多数图标被 R8 裁掉，收益为负。
**硬规则**：所有图标写进 `app/src/main/res/drawable/ic_*.xml`，用 `painterResource` 引用。代码中禁止出现 `Icons.Filled.*` 与 `Icons.Outlined.*` 等任何 `androidx.compose.material.icons` 符号。

### 2. 矢量图标不引用 `?attr/`

**背景原因**：本项目主题宿主是框架的 `Theme.DeviceDefault.DayNight`，不依赖 AppCompat，因而不提供 `colorControlNormal` 这类 AppCompat 属性。图标里写 `android:tint="?attr/colorControlNormal"` 会让 `processDebugResources` 报 `resource attr/colorControlNormal not found`（模块 5B 实际发生）。
**硬规则**：矢量 drawable 不写 `android:tint`，`android:fillColor` 只用字面量。上色交给 Compose 的 `Icon`（自动取 `LocalContentColor`）。

### 3. 禁止引入 `material-icons-extended`

**背景原因**：该 artifact 体积与 Debug 构建时间成本都很高，而项目实际用到的图标数量是个位数，手写 pathData 的成本远低于引入依赖。
**硬规则**：`libs.versions.toml` 与任何 `build.gradle.kts` 中不得出现 `material-icons-extended`。需要新图标时手写 pathData 存为本地矢量。

### 4. 不声明 `org.jetbrains.kotlin.android` 插件

**背景原因**：AGP 9.4.1 内置 Kotlin 支持，再应用该插件会被 AGP 直接拒绝构建。
**硬规则**：`app` / `benchmark` / `baselineprofile` 三个模块都不得声明该插件；只用 `kotlin.compose` 与 `kotlin.serialization`。

### 5. 版本目录别名不得含 Kotlin 保留字

**背景原因**：Gradle 由别名生成访问器，别名 `androidx-compose-material3-window-size-class` 生成的访问器含保留字 `class`，Gradle 直接报错拒绝（模块 5B 实际发生）。
**硬规则**：artifact 名含 `class` / `object` / `fun` 等 Kotlin 关键字时，在别名里改写（如 `-window-size`），并在该行上方写注释说明改写原因。

### 6. Room 升级必须配真实迁移

**背景原因**：项目已有 MIGRATION_1_2、MIGRATION_2_3 两条手写 DDL，且导出了 schema json；一旦启用 destructive fallback，用户升级即丢数据且无任何提示。
**硬规则**：禁止 `fallbackToDestructiveMigration`。每次改 entity 都要：升 `AppDatabase.version`、写 `MigrationNToN+1`、注册进 `Migrations.ALL`、提交生成的 `app/schemas/.../N.json`、补一条 androidTest 迁移测试。

### 7. 系统实例化的组件走 `@EntryPoint`

**背景原因**：Glance 小组件、WorkManager Worker、BroadcastReceiver 都由系统 `new` 出来，拿不到 Hilt 图；为了注入而引入 hilt-work 会把 WorkManager 的初始化也接管掉。
**硬规则**：这类组件用 `@EntryPoint` + `EntryPointAccessors.fromApplication` 取依赖，不引入 `androidx.hilt:hilt-work`，不改 `InitializationProvider`。

### 8. 时间与「今天」必须走 `TimeProvider`

**背景原因**：直接调 `System.currentTimeMillis()` / `LocalDate.now()` 的代码无法在 JVM 里断言跨年、跨周、跨学期与时段边界。
**硬规则**：生产代码禁止直接读系统时钟，一律注入 `TimeProvider`（`epochMillis` / `elapsedRealtimeMillis` / `today`）。纯函数接收时间参数，不在内部取当前时间。

### 9. 课程删除只能走 `DeleteCourseUseCase`

**背景原因**：`calendar_event_links` 的外键是 `ON DELETE CASCADE`，先删课程会让 link 记录先消失，系统日历里的事件从此再也找不到归属，只能靠 `pruneOrphanEvents` 事后兜底。
**硬规则**：任何删除入口都必须调 `DeleteCourseUseCase`（内部顺序：先 `CalendarRepository.deleteEventsForCourse`，后 `TimetableRepository.deleteCourse`）。禁止在 UI 或 ViewModel 里直接调 `TimetableRepository.deleteCourse`。
