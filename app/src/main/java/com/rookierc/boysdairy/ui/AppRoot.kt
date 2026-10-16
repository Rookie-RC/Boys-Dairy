package com.rookierc.boysdairy.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.SettingsRepository
import com.rookierc.boysdairy.data.ThemeMode
import com.rookierc.boysdairy.security.LockManager
import com.rookierc.boysdairy.security.LockState
import com.rookierc.boysdairy.ui.lock.LockScreen
import com.rookierc.boysdairy.ui.theme.BoysDairyTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

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
 */
@Composable
fun BoysDairyRoot(
    viewModel: RootViewModel = viewModel(factory = RootViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()

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
                LockState.Unlocked -> MainNavigation()
            }
        }
    }
}

/** Keeps status/navigation bar icon colors in sync with the in-app theme (not just the system one). */
@Composable
private fun SystemBarsEffect(darkTheme: Boolean) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    DisposableEffect(activity, darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
                detectDarkMode = { darkTheme },
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                darkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b),
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
