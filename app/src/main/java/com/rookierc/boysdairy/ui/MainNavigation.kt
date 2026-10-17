package com.rookierc.boysdairy.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.data.Edition
import com.rookierc.boysdairy.ui.add.AddEntryScreen
import com.rookierc.boysdairy.ui.add.AddEntryViewModel
import com.rookierc.boysdairy.ui.add.EntryUndo
import com.rookierc.boysdairy.ui.ai.AiScreen
import com.rookierc.boysdairy.ui.backup.BackupFlowHost
import com.rookierc.boysdairy.ui.backup.BackupViewModel
import com.rookierc.boysdairy.ui.backup.FileActions
import com.rookierc.boysdairy.ui.common.EditionEmoji
import com.rookierc.boysdairy.ui.common.FunSnackbarController
import com.rookierc.boysdairy.ui.common.FunSnackbarHost
import com.rookierc.boysdairy.ui.common.LocalEdition
import com.rookierc.boysdairy.ui.common.LocalSnackbar
import com.rookierc.boysdairy.ui.common.NoInsets
import com.rookierc.boysdairy.ui.common.stringResource
import com.rookierc.boysdairy.ui.history.HistoryScreen
import com.rookierc.boysdairy.ui.home.HomeScreen
import com.rookierc.boysdairy.ui.insights.InsightsScreen
import com.rookierc.boysdairy.ui.settings.ChangePinScreen
import com.rookierc.boysdairy.ui.settings.SettingsScreen
import com.rookierc.boysdairy.ui.theme.FunTheme
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val AI = "ai"
    const val SETTINGS = "settings"

    /**
     * Add / edit form. Both query arguments are optional (see [addEntry]); their names are
     * [AddEntryViewModel.ARG_ENTRY_ID] and [AddEntryViewModel.ARG_EPOCH_DAY].
     */
    const val ADD = "add?entryId={entryId}&date={epochDay}"

    /**
     * Route to the add form: with [entryId] it edits that entry, otherwise it logs a new one,
     * back-filled for [date] if given. No arguments = plain "new entry".
     */
    fun addEntry(entryId: Long? = null, date: LocalDate? = null): String {
        val query = listOfNotNull(
            entryId?.let { "entryId=$it" },
            date?.let { "date=${it.toEpochDay()}" },
        )
        return if (query.isEmpty()) "add" else "add?" + query.joinToString("&")
    }

    const val CHANGE_PIN = "change_pin"
    const val INSIGHTS = "insights"
}

/** Left two tabs, then the raised "check in" button, then the right two tabs. */
private fun bottomTabsFor(edition: Edition) = listOf(
    BottomTab(Routes.HOME, R.string.tab_home, "🏠"),
    BottomTab(Routes.HISTORY, R.string.tab_history, EditionEmoji.TAPE.of(edition)),
    BottomTab(Routes.AI, R.string.tab_ai, "🤖"),
    BottomTab(Routes.SETTINGS, R.string.tab_settings, "⚙️"),
)

/**
 * Main UI shown after unlocking: four tabs plus a raised check-in button; Add, Change PIN and
 * Insights are full-screen routes without the bar.
 *
 * @param backupViewModel Activity-scoped (survives the lock that the system file picker causes)
 * @param fileActions picker launchers owned by the root composable
 */
@Composable
fun MainNavigation(
    backupViewModel: BackupViewModel,
    fileActions: FileActions,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val edition = LocalEdition.current
    val bottomTabs = remember(edition) { bottomTabsFor(edition) }
    val selectedTabRoute = bottomTabs.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }?.route

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbar = remember(snackbarHostState, scope) { FunSnackbarController(snackbarHostState, scope) }

    // Set by the AI tab ("go set the key"); consumed by the Settings tab.
    var openKeyDialogRequest by remember { mutableStateOf(false) }

    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            containerColor = FunTheme.colors.background,
            bottomBar = {
                if (selectedTabRoute != null) {
                    FunBottomBar(
                        tabs = bottomTabs,
                        selectedRoute = selectedTabRoute,
                        onTabClick = { tab -> openTab(tab.route) },
                        onAddClick = {
                            navController.navigate(Routes.addEntry()) { launchSingleTop = true }
                        },
                    )
                }
            },
            snackbarHost = { FunSnackbarHost(hostState = snackbarHostState) },
            // Each screen owns its own insets handling; the bottom bar covers the navigation bar.
            contentWindowInsets = NoInsets,
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(innerPadding),
                    enterTransition = { fadeIn(tween(160)) },
                    exitTransition = { fadeOut(tween(110)) },
                    popEnterTransition = { fadeIn(tween(160)) },
                    popExitTransition = { fadeOut(tween(110)) },
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onOpenInsights = {
                                navController.navigate(Routes.INSIGHTS) { launchSingleTop = true }
                            },
                            onEditEntry = { id ->
                                navController.navigate(Routes.addEntry(entryId = id)) { launchSingleTop = true }
                            },
                            onAddForDate = { date ->
                                navController.navigate(Routes.addEntry(date = date)) { launchSingleTop = true }
                            },
                            onBackUpNow = { openTab(Routes.SETTINGS) },
                        )
                    }
                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            onEditEntry = { id ->
                                navController.navigate(Routes.addEntry(entryId = id)) { launchSingleTop = true }
                            },
                        )
                    }
                    composable(Routes.AI) {
                        AiScreen(
                            onOpenAiKeySettings = {
                                openKeyDialogRequest = true
                                openTab(Routes.SETTINGS)
                            },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onChangePinClick = {
                                navController.navigate(Routes.CHANGE_PIN) { launchSingleTop = true }
                            },
                            fileActions = fileActions,
                            openKeyDialogRequest = openKeyDialogRequest,
                            onKeyDialogRequestConsumed = { openKeyDialogRequest = false },
                        )
                    }
                    composable(
                        route = Routes.ADD,
                        arguments = listOf(
                            navArgument(AddEntryViewModel.ARG_ENTRY_ID) {
                                type = NavType.LongType
                                defaultValue = AddEntryViewModel.NO_ENTRY_ID
                            },
                            navArgument(AddEntryViewModel.ARG_EPOCH_DAY) {
                                type = NavType.LongType
                                defaultValue = AddEntryViewModel.NO_EPOCH_DAY
                            },
                        ),
                    ) { formEntry ->
                        // Pops only while the form is on top, so a double tap can't pop the tab below.
                        val closeForm = {
                            if (navController.currentBackStackEntry?.id == formEntry.id) {
                                navController.popBackStack()
                            }
                        }
                        val undoLabel = stringResource(R.string.action_undo)
                        val undoFailedMessage = stringResource(R.string.undo_failed)
                        // "Undo" of a save / delete in the form. By the time it is tapped the form and
                        // its ViewModel are gone, so it runs in `scope` (this composition's
                        // rememberCoroutineScope): that belongs to the unlocked session and is
                        // cancelled when the app locks, like everything else of the session.
                        val runUndo: (EntryUndo) -> Unit = { undo ->
                            scope.launch {
                                try {
                                    undo()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    snackbar.show(undoFailedMessage)
                                }
                            }
                        }
                        AddEntryScreen(
                            onClose = { closeForm() },
                            onSaved = { message, celebrate, undo ->
                                closeForm()
                                snackbar.show(
                                    message = message,
                                    actionLabel = undo?.let { undoLabel },
                                    onAction = undo?.let { action -> { runUndo(action) } },
                                    long = celebrate,
                                )
                            },
                            onDeleted = { message, undo ->
                                closeForm()
                                snackbar.show(message, actionLabel = undoLabel, onAction = { runUndo(undo) })
                            },
                            onEntryMissing = { message ->
                                closeForm()
                                snackbar.show(message)
                            },
                        )
                    }
                    composable(Routes.CHANGE_PIN) {
                        ChangePinScreen(
                            onBack = { navController.popBackStack() },
                            onFinished = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.INSIGHTS) {
                        InsightsScreen(onBack = { navController.popBackStack() })
                    }
                }

                // Dialogs of the backup flow; composed only while unlocked (see BackupViewModel).
                BackupFlowHost(viewModel = backupViewModel)
            }
        }
    }
}
