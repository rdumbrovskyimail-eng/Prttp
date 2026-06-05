package com.translator.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.translator.app.presentation.crisis.CrisisResourcesScreen
import com.translator.app.presentation.debug.DebugLogsScreen
import com.translator.app.presentation.journal.JournalScreen
import com.translator.app.presentation.onboarding.OnboardingScreen
import com.translator.app.presentation.profile.ProfileScreen
import com.translator.app.presentation.settings.SettingsScreen
import com.translator.app.presentation.therapy.TherapyRoute
import com.translator.app.presentation.theme.ThemeViewModel
import com.translator.app.presentation.translator.MinimalTranslateScreen
import com.translator.app.presentation.translator.TranslateScreen
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "onboarding") {
        composable("onboarding") {
            OnboardingScreen(onNavigateToTranslator = {
                navController.navigate("translator") { popUpTo("onboarding") { inclusive = true } }
            })
        }
        composable("translator") {
            val themeVm: ThemeViewModel = hiltViewModel()
            val layout by themeVm.layoutId.collectAsStateWithLifecycle()
            if (layout == "MINIMAL") {
                MinimalTranslateScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToLogs = { navController.navigate("logs") },
                    onBack = { navController.popBackStack() }
                )
            } else {
                TranslateScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToLogs = { navController.navigate("logs") },
                    onBack = { navController.popBackStack() }
                )
            }
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
        composable("therapy") { TherapyRoute(onOpenResources = { navController.navigate("crisis") }) }
        composable("journal") { JournalScreen() }
        composable("profile") { ProfileScreen() }
        composable("crisis") { CrisisResourcesScreen() }
    }
}
