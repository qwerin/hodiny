package cz.hodiny.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import cz.hodiny.HodinyApp
import cz.hodiny.ui.screens.*

sealed class Screen(val route: String, val label: String) {
    object Home : Screen("home", "Dnes")
    object History : Screen("history", "Historie")
    object Export : Screen("export", "Export")
    object Settings : Screen("settings", "Nastavení")
    object Onboarding : Screen("onboarding", "Nastavení")
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as HodinyApp
    val navController = rememberNavController()

    val settings by app.preferences.settings.collectAsState(initial = null)
    val isOnboarded = settings?.isOnboarded ?: return

    val startDestination = if (isOnboarded) Screen.Home.route else Screen.Onboarding.route

    val bottomItems = listOf(Screen.Home, Screen.History, Screen.Export, Screen.Settings)

    Scaffold(
        bottomBar = {
            if (isOnboarded) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                NavigationBar {
                    bottomItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                when (screen) {
                                    Screen.Home -> Icon(Icons.Default.Home, null)
                                    Screen.History -> Icon(Icons.Default.List, null)
                                    Screen.Export -> Icon(Icons.Default.Share, null)
                                    Screen.Settings -> Icon(Icons.Default.Settings, null)
                                    else -> {}
                                }
                            },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) { HomeScreen(padding) }
            composable(Screen.History.route) { HistoryScreen(padding) }
            composable(Screen.Export.route) { ExportScreen(padding) }
            composable(Screen.Settings.route) { SettingsScreen(padding) }
        }
    }
}
