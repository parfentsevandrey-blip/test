package com.example.retrodatabases.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.retrodatabases.ui.screens.AssistantScreen
import com.example.retrodatabases.ui.screens.DatabasesScreen
import com.example.retrodatabases.ui.screens.RecordDetailScreen
import com.example.retrodatabases.ui.screens.RecordsScreen

private object Route {
    const val DATABASES = "databases"
    const val RECORDS = "records/{databaseId}"
    const val RECORD = "record/{recordId}"
    const val ASSISTANT = "assistant"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.DATABASES) {
        composable(Route.DATABASES) {
            DatabasesScreen(
                onOpenDatabase = { databaseId -> navController.navigate("records/$databaseId") },
                onOpenAssistant = { navController.navigate(Route.ASSISTANT) },
            )
        }

        composable(Route.ASSISTANT) {
            AssistantScreen(
                onBack = { navController.popBackStack() },
                onOpenRecord = { recordId -> navController.navigate("record/$recordId") },
            )
        }

        composable(
            route = Route.RECORDS,
            arguments = listOf(navArgument("databaseId") { type = NavType.LongType }),
        ) { entry ->
            RecordsScreen(
                databaseId = entry.arguments?.getLong("databaseId") ?: 0L,
                onBack = { navController.popBackStack() },
                onOpenRecord = { recordId -> navController.navigate("record/$recordId") },
            )
        }

        composable(
            route = Route.RECORD,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
        ) { entry ->
            RecordDetailScreen(
                recordId = entry.arguments?.getLong("recordId") ?: 0L,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
