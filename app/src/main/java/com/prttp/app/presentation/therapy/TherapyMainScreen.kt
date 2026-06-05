package com.prttp.app.presentation.therapy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prttp.app.presentation.theme.LocalAppPalette
import com.prttp.app.presentation.specializations.SpecializationsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TherapyMainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToCrisis: () -> Unit
) {
    val palette = LocalAppPalette.current
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Пси-Сессия"
                            1 -> "Дневник мыслей"
                            2 -> "Профиль пациента"
                            else -> "Направления"
                        },
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToLogs) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Логи",
                            tint = palette.textPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Настройки",
                            tint = palette.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.textPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = palette.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Mic, contentDescription = "Сессия") },
                    label = { Text("Сессия") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.accentPrimary,
                        selectedTextColor = palette.accentPrimary,
                        unselectedIconColor = palette.textSecondary,
                        unselectedTextColor = palette.textSecondary,
                        indicatorColor = palette.accentSoft
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Book, contentDescription = "Дневник") },
                    label = { Text("Дневник") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.accentPrimary,
                        selectedTextColor = palette.accentPrimary,
                        unselectedIconColor = palette.textSecondary,
                        unselectedTextColor = palette.textSecondary,
                        indicatorColor = palette.accentSoft
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Профиль") },
                    label = { Text("Профиль") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.accentPrimary,
                        selectedTextColor = palette.accentPrimary,
                        unselectedIconColor = palette.textSecondary,
                        unselectedTextColor = palette.textSecondary,
                        indicatorColor = palette.accentSoft
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Psychology, contentDescription = "Работа") },
                    label = { Text("Работа") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.accentPrimary,
                        selectedTextColor = palette.accentPrimary,
                        unselectedIconColor = palette.textSecondary,
                        unselectedTextColor = palette.textSecondary,
                        indicatorColor = palette.accentSoft
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(palette.background)
        ) {
            when (selectedTab) {
                0 -> TherapyRoute(onOpenResources = onNavigateToCrisis)
                1 -> JournalScreen()
                2 -> ProfileScreen()
                3 -> SpecializationsScreen()
            }
        }
    }
}