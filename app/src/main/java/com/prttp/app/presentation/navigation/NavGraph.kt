package com.prttp.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prttp.app.presentation.debug.DebugLogsScreen
import com.prttp.app.presentation.onboarding.OnboardingScreen
import com.prttp.app.presentation.settings.SettingsScreen
import com.prttp.app.presentation.therapy.CrisisResourcesScreen
import com.prttp.app.presentation.therapy.TherapyMainScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "onboarding") {
        composable("onboarding") {
            OnboardingScreen(onNavigateToMain = {
                navController.navigate("main") { popUpTo("onboarding") { inclusive = true } }
            })
        }
        composable("main") {
            TherapyMainScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToLogs = { navController.navigate("logs") },
                onNavigateToCrisis = { navController.navigate("crisis") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLogs = { navController.navigate("logs") }
            )
        }
        composable("logs") {
            DebugLogsScreen(onBack = { navController.popBackStack() })
        }
        composable("crisis") {
            CrisisResourcesScreen()
        }
    }
}