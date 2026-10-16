@file:OptIn(ExperimentalLayoutApi::class)

package com.rookierc.boysdairy.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.BuildConfig
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.data.ThemeMode
import com.rookierc.boysdairy.ui.backup.FileActions
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.ChunkyButton
import com.rookierc.boysdairy.ui.common.EmojiChip
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.FunConfirmDialog
import com.rookierc.boysdairy.ui.common.FunDialog
import com.rookierc.boysdairy.ui.common.FunTextField
import com.rookierc.boysdairy.ui.common.ScreenTitle
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.StickerBadge
import com.rookierc.boysdairy.ui.theme.FunTheme

/**
 * Settings tab.
 *
 * @param fileActions system-picker launchers owned by the root composable (see BackupViewModel)
 * @param openKeyDialogRequest set by the AI tab's "go set the key" button; opens the key dialog
 */
@Composable
fun SettingsScreen(
    onChangePinClick: () -> Unit,
    fileActions: FileActions,
    openKeyDialogRequest: Boolean,
    onKeyDialogRequestConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    var language by remember { mutableStateOf(currentAppLanguage()) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showClearKeyConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(openKeyDialogRequest) {
        if (openKeyDialogRequest) {
            showKeyDialog = true
            onKeyDialogRequestConsumed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(
            emoji = "⚙️",
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
        )

        // Appearance
        SettingsSection(emoji = "🎨", title = stringResource(R.string.settings_appearance)) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.labelLarge,
                color = FunTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    EmojiChip(
                        label = stringResource(mode.labelRes()),
                        emoji = mode.emoji(),
                        selected = mode == themeMode,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.labelLarge,
                color = FunTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLanguage.entries.forEach { option ->
                    EmojiChip(
                        label = stringResource(option.labelRes()),
                        emoji = option.emoji(),
                        selected = option == language,
                        selectedColor = FunTheme.colors.mint,
                        onClick = {
                            if (option != language) {
                                language = option
                                applyAppLanguage(option)
                            }
                        },
                    )
                }
            }
        }

        // Security
        SettingsSection(emoji = "🔐", title = stringResource(R.string.settings_security)) {
            SettingsRow(
                emoji = "🔑",
                title = stringResource(R.string.settings_change_pin),
                subtitle = stringResource(R.string.settings_change_pin_desc),
                onClick = onChangePinClick,
            )
        }

        // Data
        SettingsSection(emoji = "💾", title = stringResource(R.string.settings_data)) {
            SettingsRow(
                emoji = "📤",
                title = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_desc),
                onClick = fileActions.launchExport,
            )
            RowDivider()
            SettingsRow(
                emoji = "📥",
                title = stringResource(R.string.settings_import),
                subtitle = stringResource(R.string.settings_import_desc),
                onClick = fileActions.launchImport,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_data_note),
                style = MaterialTheme.typography.bodyMedium,
                color = FunTheme.colors.textSecondary,
            )
        }

        // AI
        SettingsSection(emoji = "🤖", title = stringResource(R.string.settings_ai)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_ai_key),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                when (hasApiKey) {
                    true -> StickerBadge(
                        text = stringResource(R.string.settings_ai_key_set),
                        containerColor = FunTheme.colors.mint,
                        rotation = 2f,
                    )
                    false -> StickerBadge(
                        text = stringResource(R.string.settings_ai_key_unset),
                        containerColor = FunTheme.colors.surfaceAlt,
                        contentColor = FunTheme.colors.textPrimary,
                        rotation = 2f,
                    )
                    null -> Unit
                }
            }
            Spacer(Modifier.height(12.dp))
            ChunkyButton(
                text = stringResource(
                    if (hasApiKey == true) R.string.settings_ai_key_change else R.string.settings_ai_key_set_action,
                ),
                emoji = "🔑",
                onClick = { showKeyDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasApiKey == true) {
                Spacer(Modifier.height(10.dp))
                ChunkyButton(
                    text = stringResource(R.string.settings_ai_key_clear),
                    emoji = "🧹",
                    onClick = { showClearKeyConfirm = true },
                    containerColor = FunTheme.colors.surface,
                    contentColor = FunTheme.colors.textPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_ai_note),
                style = MaterialTheme.typography.bodyMedium,
                color = FunTheme.colors.textSecondary,
            )
        }

        // About
        SettingsSection(emoji = "🧮", title = stringResource(R.string.settings_about)) {
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_about_joke),
                style = MaterialTheme.typography.bodyMedium,
                color = FunTheme.colors.textSecondary,
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showKeyDialog) {
        ApiKeyDialog(
            onDismiss = { showKeyDialog = false },
            onSave = { key ->
                viewModel.saveApiKey(key)
                showKeyDialog = false
            },
        )
    }

    if (showClearKeyConfirm) {
        FunConfirmDialog(
            title = stringResource(R.string.settings_ai_key_clear_title),
            emoji = "🧹",
            message = stringResource(R.string.settings_ai_key_clear_message),
            confirmText = stringResource(R.string.settings_ai_key_clear_confirm),
            confirmColor = FunTheme.colors.danger,
            onConfirm = {
                viewModel.clearApiKey()
                showClearKeyConfirm = false
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showClearKeyConfirm = false },
        )
    }
}

@Composable
private fun SettingsSection(
    emoji: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    FunCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(emoji = emoji, title = title)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsRow(
    emoji: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiText(text = emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FunTheme.colors.textSecondary,
                )
            }
        }
        Text(text = "›", fontSize = 24.sp, color = FunTheme.colors.textSecondary)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = FunTheme.colors.ink.copy(alpha = 0.15f), thickness = 1.dp)
}

/** Password-style input for the Claude API key. The text only lives in this dialog's state. */
@Composable
private fun ApiKeyDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val toggleCd = stringResource(if (visible) R.string.settings_key_hide else R.string.settings_key_show)

    FunDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_key_dialog_title),
        emoji = "🔑",
        buttons = {
            ChunkyButton(
                text = stringResource(R.string.settings_key_save),
                emoji = "💾",
                onClick = { onSave(key.trim()) },
                enabled = key.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                containerColor = FunTheme.colors.surface,
                contentColor = FunTheme.colors.textPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Text(
            text = stringResource(R.string.settings_key_dialog_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        FunTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.settings_key_placeholder),
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailing = {
                IconButton(onClick = { visible = !visible }) {
                    Text(
                        text = if (visible) "🙈" else "👁️",
                        fontSize = 20.sp,
                        modifier = Modifier.semantics { contentDescription = toggleCd },
                    )
                }
            },
        )
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun ThemeMode.emoji(): String = when (this) {
    ThemeMode.SYSTEM -> "📱"
    ThemeMode.LIGHT -> "☀️"
    ThemeMode.DARK -> "🌙"
}

@StringRes
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.CHINESE -> R.string.language_chinese
    AppLanguage.ENGLISH -> R.string.language_english
}

private fun AppLanguage.emoji(): String = when (this) {
    AppLanguage.SYSTEM -> "🌐"
    AppLanguage.CHINESE -> "🀄"
    AppLanguage.ENGLISH -> "🔤"
}
