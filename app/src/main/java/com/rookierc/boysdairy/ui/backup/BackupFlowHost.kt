package com.rookierc.boysdairy.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.data.backup.BackupError
import com.rookierc.boysdairy.data.backup.BackupResult
import com.rookierc.boysdairy.ui.common.ChunkyButton
import com.rookierc.boysdairy.ui.common.FunConfirmDialog
import com.rookierc.boysdairy.ui.common.FunDialog
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.theme.FunTheme

/**
 * Renders the backup flow dialogs. It is composed inside the unlocked main UI only, which is
 * what defers a picked file's export/import until the PIN has been entered again.
 */
@Composable
fun BackupFlowHost(viewModel: BackupViewModel) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()

    val current = phase

    if (current is BackupPhase.ExportPicked) {
        LaunchedEffect(current.uri) { viewModel.runExport(current.uri) }
    }
    // One stable dialog from "picked" through "working", so it does not flicker between phases.
    if (current is BackupPhase.ExportPicked || current is BackupPhase.Working) {
        WorkingDialog()
    }

    when (current) {
        is BackupPhase.ChooseImportMode -> FunDialog(
            onDismissRequest = viewModel::dismiss,
            title = stringResource(R.string.backup_import_title),
            emoji = "📥",
            buttons = {
                ChunkyButton(
                    text = stringResource(R.string.backup_mode_merge),
                    emoji = "🤝",
                    onClick = { viewModel.chooseMerge(current.uri) },
                    containerColor = FunTheme.colors.mint,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChunkyButton(
                    text = stringResource(R.string.backup_mode_replace),
                    emoji = "💣",
                    onClick = { viewModel.chooseReplace(current.uri) },
                    containerColor = FunTheme.colors.danger,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChunkyButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = viewModel::dismiss,
                    containerColor = FunTheme.colors.surface,
                    contentColor = FunTheme.colors.textPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.backup_mode_merge_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.backup_mode_replace_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is BackupPhase.ConfirmReplace -> FunConfirmDialog(
            title = stringResource(R.string.backup_replace_title),
            emoji = "💣",
            message = stringResource(R.string.backup_replace_message),
            confirmText = stringResource(R.string.backup_replace_confirm),
            confirmColor = FunTheme.colors.danger,
            onConfirm = { viewModel.confirmReplace(current.uri) },
            dismissText = stringResource(R.string.backup_replace_cancel),
            onDismiss = viewModel::dismiss,
        )

        is BackupPhase.Done -> ResultDialog(result = current.result, onDismiss = viewModel::dismiss)

        BackupPhase.Idle, is BackupPhase.ExportPicked, BackupPhase.Working -> Unit
    }
}

@Composable
private fun WorkingDialog() {
    FunDialog(
        onDismissRequest = {},
        title = stringResource(R.string.backup_working),
        emoji = "⏳",
        dismissible = false,
        buttons = {},
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = FunTheme.colors.pink)
        }
    }
}

private class ResultCopy(val title: String, val emoji: String, val lines: List<String>)

@Composable
private fun ResultDialog(result: BackupResult, onDismiss: () -> Unit) {
    val copy = when (result) {
        is BackupResult.Exported -> ResultCopy(
            title = stringResource(R.string.backup_export_done_title),
            emoji = "🎉",
            lines = listOf(quantityString(R.plurals.backup_exported_count, result.count, result.count)),
        )

        is BackupResult.Imported -> ResultCopy(
            title = stringResource(R.string.backup_import_done_title),
            emoji = "✅",
            lines = listOf(
                quantityString(R.plurals.backup_imported_added, result.added, result.added),
                quantityString(
                    R.plurals.backup_imported_skipped,
                    result.skippedDuplicates,
                    result.skippedDuplicates,
                ),
            ),
        )

        is BackupResult.Failed -> ResultCopy(
            title = stringResource(R.string.backup_failed_title),
            emoji = "😵",
            lines = listOf(stringResource(result.error.messageRes())),
        )
    }

    FunDialog(
        onDismissRequest = onDismiss,
        title = copy.title,
        emoji = copy.emoji,
        buttons = {
            ChunkyButton(
                text = stringResource(R.string.action_ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            copy.lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@androidx.annotation.StringRes
private fun BackupError.messageRes(): Int = when (this) {
    BackupError.IO -> R.string.backup_error_io
    BackupError.INVALID_FORMAT -> R.string.backup_error_invalid_format
    BackupError.UNSUPPORTED_VERSION -> R.string.backup_error_unsupported_version
    BackupError.EMPTY -> R.string.backup_error_empty
}
