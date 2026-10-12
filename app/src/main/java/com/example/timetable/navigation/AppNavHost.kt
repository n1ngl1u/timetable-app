package com.example.timetable.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.timetable.ui.calendar.CalendarScreen
import com.example.timetable.ui.puremode.PureModeViewModel
import com.example.timetable.ui.settings.SettingsScreen
import com.example.timetable.ui.timetable.TimetableScreen

/**
 * 应用外壳。两件事在这里集中处理，避免散进各页面：
 * 1. 导航容器（底部栏 / 侧栏）与 NavHost 宿主，每个页面自带 TopAppBar；
 * 2. 纯净模式：不渲染导航容器、接管返回键、进入时直达课表。
 *
 * 选中态直接派生自 NavController 的返回栈 —— rememberNavController 内部就是 rememberSaveable，
 * 进程重建后自动恢复，再存一份 rememberSaveable 的 selected 只会制造第二个真源。
 */
@Composable
fun AppNavHost(
    windowSizeClass: WindowSizeClass,
    autoImportPayload: String?,
    pureMode: PureModeViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selected = AppDestination.entries.firstOrNull { destination ->
        currentDestination?.matches(destination) == true
    } ?: AppDestination.TIMETABLE
    val pureModeEnabled by pureMode.enabled.collectAsStateWithLifecycle()
    val onTimetableRoute = currentDestination?.matches(AppDestination.TIMETABLE) == true

    // 进入纯净模式时直达课表：导航容器被隐藏后用户没有别的入口
    LaunchedEffect(pureModeEnabled) {
        if (pureModeEnabled && !onTimetableRoute) {
            navController.navigateTo(AppDestination.TIMETABLE)
        }
    }

    // 纯净模式接管返回键；关闭时完全不注册，返回键仍归系统与各页面。
    // ModalBottomSheet 是独立 Dialog 窗口，返回键由 Dialog 自身在窗口层消费，
    // 优先于这里的 OnBackPressedDispatcher 回调，所以底部抽屉的行为不受影响。
    BackHandler(enabled = pureModeEnabled) {
        if (pureMode.onBackPressed() && !onTimetableRoute) {
            // 防御性检查：退出瞬间如果不在课表页，先回课表页再交还界面
            navController.navigateTo(AppDestination.TIMETABLE)
        }
    }

    when {
        pureModeEnabled -> {
            AppNavHostContent(
                navController = navController,
                autoImportPayload = autoImportPayload,
                modifier = Modifier.fillMaxSize(),
            )
        }
        windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact -> {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    AppDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == selected,
                            onClick = { navController.navigateTo(destination) },
                            icon = { destination.icon() },
                            label = { Text(text = stringResource(destination.labelRes)) },
                        )
                    }
                }
                AppNavHostContent(
                    navController = navController,
                    autoImportPayload = autoImportPayload,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                )
            }
        }
        else -> {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        AppDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = destination == selected,
                                onClick = { navController.navigateTo(destination) },
                                icon = { destination.icon() },
                                label = { Text(text = stringResource(destination.labelRes)) },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    AppNavHostContent(
                        navController = navController,
                        autoImportPayload = autoImportPayload,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHostContent(
    navController: NavHostController,
    autoImportPayload: String?,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TimetableRoute,
        modifier = modifier,
    ) {
        // ImportBenchmark 依赖这条链路：Intent extra -> MainActivity -> AppNavHost 参数 -> 课表页
        composable<TimetableRoute> {
            TimetableScreen(autoImportPayload = autoImportPayload)
        }
        composable<CalendarRoute> {
            CalendarScreen()
        }
        composable<SettingsRoute> {
            SettingsScreen()
        }
    }
}

private fun NavDestination.matches(destination: AppDestination): Boolean =
    hierarchy.any { node -> node.hasRoute(destination.route::class) }

private fun NavHostController.navigateTo(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
