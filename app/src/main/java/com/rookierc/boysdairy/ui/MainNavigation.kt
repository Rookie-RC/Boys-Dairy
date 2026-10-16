package com.rookierc.boysdairy.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.ui.add.AddEntryScreen
import com.rookierc.boysdairy.ui.common.NoInsets
import com.rookierc.boysdairy.ui.history.HistoryScreen
import com.rookierc.boysdairy.ui.home.HomeScreen
import com.rookierc.boysdairy.ui.settings.ChangePinScreen
import com.rookierc.boysdairy.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ADD = "add"
    const val CHANGE_PIN = "change_pin"
}

private data class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, R.string.tab_home, Icons.Filled.Home),
    TopLevelDestination(Routes.HISTORY, R.string.tab_history, Icons.Filled.History),
    TopLevelDestination(Routes.SETTINGS, R.string.tab_settings, Icons.Filled.Settings),
)

/** Main UI shown after unlocking: bottom-bar tabs plus full-screen Add and Change PIN routes. */
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = topLevelDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
        // Each screen owns its own insets handling; the bottom bar covers the navigation bar.
        contentWindowInsets = NoInsets,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(onAddClick = { navController.navigate(Routes.ADD) { launchSingleTop = true } })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onAddClick = { navController.navigate(Routes.ADD) { launchSingleTop = true } })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onChangePinClick = {
                        navController.navigate(Routes.CHANGE_PIN) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.ADD) {
                AddEntryScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.CHANGE_PIN) {
                ChangePinScreen(
                    onBack = { navController.popBackStack() },
                    onFinished = { navController.popBackStack() },
                )
            }
        }
    }
}
