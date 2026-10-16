package com.rookierc.boysdairy.ui.backup

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.backup.BackupError
import com.rookierc.boysdairy.data.backup.BackupManager
import com.rookierc.boysdairy.data.backup.BackupResult
import com.rookierc.boysdairy.data.backup.ImportMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The backup flow as a small state machine; the UI ([BackupFlowHost]) just renders it. */
sealed interface BackupPhase {
    data object Idle : BackupPhase

    /** A destination file was picked; export starts as soon as the UI is unlocked. */
    data class ExportPicked(val uri: Uri) : BackupPhase

    /** A backup file was picked; the user now chooses merge or replace. */
    data class ChooseImportMode(val uri: Uri) : BackupPhase

    /** The user chose "replace": one more scary confirmation. */
    data class ConfirmReplace(val uri: Uri) : BackupPhase

    data object Working : BackupPhase

    data class Done(val result: BackupResult) : BackupPhase
}

/**
 * Owns the whole backup flow.
 *
 * IMPORTANT: this ViewModel is scoped to the *Activity*, not to the per-unlock session store.
 * The system file picker backgrounds the app, which locks it and tears the session (and all its
 * screens / ViewModels) down. The picked Uri and a running export/import must survive that, and
 * must only continue once the user has entered the PIN again: [BackupFlowHost] is only composed
 * while unlocked, so [ExportPicked] / [ChooseImportMode] just wait here until then.
 */
class BackupViewModel(private val backupManager: BackupManager) : ViewModel() {

    private val _phase = MutableStateFlow<BackupPhase>(BackupPhase.Idle)
    val phase: StateFlow<BackupPhase> = _phase.asStateFlow()

    /** Result of the create-document picker (`null` = user backed out). */
    fun onExportPicked(uri: Uri?) {
        if (uri == null) return
        _phase.value = BackupPhase.ExportPicked(uri)
    }

    /** Result of the open-document picker (`null` = user backed out). */
    fun onImportPicked(uri: Uri?) {
        if (uri == null) return
        _phase.value = BackupPhase.ChooseImportMode(uri)
    }

    fun runExport(uri: Uri) {
        _phase.value = BackupPhase.Working
        viewModelScope.launch {
            val result = guarded { backupManager.export(uri) }
            _phase.value = BackupPhase.Done(result)
        }
    }

    fun chooseMerge(uri: Uri) = runImport(uri, ImportMode.MERGE)

    fun chooseReplace(uri: Uri) {
        _phase.value = BackupPhase.ConfirmReplace(uri)
    }

    fun confirmReplace(uri: Uri) = runImport(uri, ImportMode.REPLACE)

    fun dismiss() {
        _phase.value = BackupPhase.Idle
    }

    private fun runImport(uri: Uri, mode: ImportMode) {
        _phase.value = BackupPhase.Working
        viewModelScope.launch {
            val result = guarded { backupManager.import(uri, mode) }
            _phase.value = BackupPhase.Done(result)
        }
    }

    private suspend fun guarded(block: suspend () -> BackupResult): BackupResult =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            BackupResult.Failed(BackupError.IO)
        }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(appContainer().backupManager) }
        }
    }
}

/**
 * The two system-picker launchers, owned by the root composable (which is never disposed while
 * the app is locked) and handed down to Settings.
 */
@Immutable
class FileActions(
    val launchExport: () -> Unit,
    val launchImport: () -> Unit,
)
