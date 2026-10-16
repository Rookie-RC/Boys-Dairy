package com.rookierc.boysdairy.ui

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.SettingsRepository
import com.rookierc.boysdairy.data.ThemeMode
import com.rookierc.boysdairy.data.backup.BackupManager
import com.rookierc.boysdairy.security.LockManager
import com.rookierc.boysdairy.security.LockState
import com.rookierc.boysdairy.ui.backup.BackupViewModel
import com.rookierc.boysdairy.ui.backup.FileActions
import com.rookierc.boysdairy.ui.lock.LockScreen
import com.rookierc.boysdairy.ui.theme.BoysDairyTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class RootViewModel(
    settings: SettingsRepository,
    lockManager: LockManager,
) : ViewModel() {

    /** `null` until the stored theme has been read, so the first frame is never the wrong theme. */
    val themeMode: StateFlow<ThemeMode?> = settings.themeMode.stateIn<ThemeMode?>(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    val lockState: StateFlow<LockState> = lockManager.state

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                RootViewModel(container.settingsRepository, container.lockManager)
            }
        }
    }
}

/**
 * Lock gate: unless the app is [LockState.Unlocked] only the lock screen is composed, so no
 * navigation graph, view model or screen content of the main UI exists while locked.
 *
 * Three things deliberately live *here* (outside the unlocked session) so they survive a lock:
 *  - the system file-picker launchers: the picker backgrounds the app, which locks it and tears
 *    the main UI down. A launcher registered inside it would be unregistered by the time the
 *    result arrives; registered here it always receives it;
 *  - [BackupViewModel] (Activity scoped): keeps the picked Uri / a running export or import;
 *  - [SessionStoreHolder]: the store that the unlocked UI's ViewModels live in.
 */
@Composable
fun BoysDairyRoot(
    viewModel: RootViewModel = viewModel(factory = RootViewModel.Factory),
    backupViewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
    sessionStores: SessionStoreHolder = viewModel(factory = SessionStoreHolder.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE),
        onResult = { uri -> backupViewModel.onExportPicked(uri) },
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> backupViewModel.onImportPicked(uri) },
    )
    val fileActions = remember(exportLauncher, importLauncher) {
        FileActions(
            launchExport = {
                exportLauncher.launch(BackupManager.suggestedFileName(LocalDate.now()))
            },
            launchImport = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            },
        )
    }

    val mode = themeMode ?: return
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    SystemBarsEffect(darkTheme = darkTheme)

    BoysDairyTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (lockState) {
                LockState.Loading -> Unit
                LockState.NeedsSetup, LockState.Locked -> LockScreen()
                LockState.Unlocked -> UnlockedSession(sessionStores) {
                    MainNavigation(
                        backupViewModel = backupViewModel,
                        fileActions = fileActions,
                    )
                }
            }
        }
    }
}

/**
 * Hosts [content] in a per-unlock-session [androidx.lifecycle.ViewModelStoreOwner]. When this
 * composable leaves the composition because the app locked, every ViewModel created inside is
 * destroyed ([SessionStoreHolder.end]); a configuration change (rotation, language switch) keeps
 * them.
 */
@Composable
private fun UnlockedSession(
    holder: SessionStoreHolder,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val activity = context.findComponentActivity()

    val owner = remember(holder.store) { SessionViewModelStoreOwner(holder.store, application) }

    DisposableEffect(holder) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                holder.end()
            }
        }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}

/** Keeps status/navigation bar icon colors in sync with the in-app theme (not just the system one). */
@Composable
private fun SystemBarsEffect(darkTheme: Boolean) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    DisposableEffect(activity, darkTheme) {
        // Both bars are transparent: the app draws its own (cream / purple) colors behind them.
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
                detectDarkMode = { darkTheme },
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
                detectDarkMode = { darkTheme },
            ),
        )
        onDispose { }
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
