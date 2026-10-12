package com.example.timetable.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.timetable.ui.root.RootScreen

sealed interface AppRoute {
    val route: String

    data object Timetable : AppRoute {
        override val route: String = "timetable"
    }
}

@Composable
fun AppNavHost(
    autoImportPayload: String?,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Timetable.route,
    ) {
        composable(AppRoute.Timetable.route) {
            RootScreen(autoImportPayload = autoImportPayload)
        }
    }
}
