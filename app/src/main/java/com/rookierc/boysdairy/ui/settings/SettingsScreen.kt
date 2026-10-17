@file:OptIn(ExperimentalLayoutApi::class)

package com.rookierc.boysdairy.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.BuildConfig
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.ai.AiProvider
import com.rookierc.boysdairy.ai.AiSelection
import com.rookierc.boysdairy.data.Edition
import com.rookierc.boysdairy.data.ThemeMode
import com.rookierc.boysdairy.security.BiometricSettingRow
import com.rookierc.boysdairy.security.PinHasher
import com.rookierc.boysdairy.ui.backup.FileActions
import com.rookierc.boysdairy.ui.common.BiometricOutcome
import com.rookierc.boysdairy.ui.common.BiometricPromptTexts
import com.rookierc.boysdairy.ui.common.ChoiceCard
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.ChunkyButton
import com.rookierc.boysdairy.ui.common.EmojiChip
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.FunConfirmDialog
import com.rookierc.boysdairy.ui.common.FunDialog
import com.rookierc.boysdairy.ui.common.FunTextField
import com.rookierc.boysdairy.ui.common.LocalEdition
import com.rookierc.boysdairy.ui.common.LocalSnackbar
import com.rookierc.boysdairy.ui.common.ScreenTitle
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.StickerBadge
import com.rookierc.boysdairy.ui.common.emoji
import com.rookierc.boysdairy.ui.common.labelRes
import com.rookierc.boysdairy.ui.common.lastExportText
import com.rookierc.boysdairy.ui.common.nameRes
import com.rookierc.boysdairy.ui.common.rememberBiometricPromptLauncher
import com.rookierc.boysdairy.ui.common.stringArrayResource
import com.rookierc.boysdairy.ui.common.stringResource
import com.rookierc.boysdairy.ui.common.taglineRes
import com.rookierc.boysdairy.ui.lock.pinMessageText
import com.rookierc.boysdairy.ui.theme.FunTheme

/**
 * Settings tab: five cards (appearance, security, data, AI, about), all collapsed by default with a
 * one-line summary of where things stand.
 *
 * @param fileActions system-picker launchers owned by the root composable (see BackupViewModel)
 * @param openKeyDialogRequest set by the AI tab's "go set the key" button; opens the key dialog
 *   and unfolds the AI card
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
    val ai by viewModel.ai.collectAsStateWithLifecycle()
    var language by remember { mutableStateOf(currentAppLanguage()) }
    val edition = LocalEdition.current
    var showKeyDialog by remember { mutableStateOf(false) }
    var showClearKeyConfirm by remember { mutableStateOf(false) }
    val hasEntries by viewModel.hasEntries.collectAsStateWithLifecycle()
    val clearAll by viewModel.clearAll.collectAsStateWithLifecycle()
    val lastExport by viewModel.lastExport.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val clearedMessages = stringArrayResource(R.array.clear_all_done_toasts)
    val biometric by viewModel.biometric.collectAsStateWithLifecycle()
    val pendingEdition by viewModel.pendingEdition.collectAsStateWithLifecycle()
    val aiIncludeNotes by viewModel.aiIncludeNotes.collectAsStateWithLifecycle()
    // Every card starts collapsed: the page reads at a glance and a stray tap cannot flip a setting.
    // Each card has its own saved UI state; none of it is persisted.
    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var securityExpanded by rememberSaveable { mutableStateOf(false) }
    var dataExpanded by rememberSaveable { mutableStateOf(false) }
    var aiExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    val summarySeparator = stringResource(R.string.settings_appearance_summary_separator)

    // The device may have gained / lost fingerprints since the last look.
    LifecycleStartEffect(viewModel) {
        viewModel.refreshBiometric()
        onStopOrDispose { }
    }
    BiometricEnableEffects(viewModel)

    LaunchedEffect(clearAll.justCleared) {
        if (clearAll.justCleared) {
            snackbar.show(clearedMessages.random())
            viewModel.onClearedNoticeShown()
        }
    }

    LaunchedEffect(openKeyDialogRequest) {
        if (openKeyDialogRequest) {
            aiExpanded = true
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
        // "主题：跟随系统 · 语言：中文 · 女生版": theme and language are prefixed, since both can read
        // "follow system"; the edition's label names itself.
        val appearanceSummary = listOf(
            stringResource(
                R.string.settings_appearance_summary_item,
                stringResource(R.string.settings_theme),
                stringResource(themeMode.labelRes()),
            ),
            stringResource(
                R.string.settings_appearance_summary_item,
                stringResource(R.string.settings_language),
                stringResource(language.labelRes()),
            ),
            stringResource(edition.labelRes()),
        ).joinToString(separator = summarySeparator)
        CollapsibleSettingsSection(
            emoji = "🎨",
            title = stringResource(R.string.settings_appearance),
            summary = appearanceSummary,
            expanded = appearanceExpanded,
            onToggle = { appearanceExpanded = !appearanceExpanded },
        ) {
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
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_edition),
                style = MaterialTheme.typography.labelLarge,
                color = FunTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Edition.entries.forEach { option ->
                    EmojiChip(
                        label = stringResource(option.labelRes()),
                        emoji = option.emoji(),
                        selected = option == edition,
                        selectedColor = FunTheme.colors.pink,
                        // Only asks; the edition changes once the dialog below is confirmed.
                        onClick = { viewModel.requestEdition(option, edition) },
                    )
                }
            }
        }

        // Security: "指纹解锁：开" where the device has a fingerprint sensor, else what the card offers.
        val securitySummary = if (biometric.row != BiometricSettingRow.HIDDEN) {
            stringResource(
                R.string.settings_appearance_summary_item,
                stringResource(R.string.settings_biometric),
                stringResource(if (biometric.checked) R.string.settings_summary_on else R.string.settings_summary_off),
            )
        } else {
            listOf(
                stringResource(R.string.settings_change_pin),
                stringResource(R.string.settings_lock_now),
            ).joinToString(separator = summarySeparator)
        }
        CollapsibleSettingsSection(
            emoji = "🔐",
            title = stringResource(R.string.settings_security),
            summary = securitySummary,
            expanded = securityExpanded,
            onToggle = { securityExpanded = !securityExpanded },
        ) {
            SettingsRow(
                emoji = "🔑",
                title = stringResource(R.string.settings_change_pin),
                subtitle = stringResource(R.string.settings_change_pin_desc),
                onClick = onChangePinClick,
            )
            if (biometric.row != BiometricSettingRow.HIDDEN) {
                RowDivider()
                BiometricSettingsRow(state = biometric, onToggle = viewModel::setBiometricUnlock)
            }
            RowDivider()
            SettingsRow(
                emoji = "🔒",
                title = stringResource(R.string.settings_lock_now),
                subtitle = stringResource(R.string.settings_lock_now_desc),
                onClick = viewModel::lockNow,
            )
        }

        // Data: "上次导出：…" / "还没导出过" (empty until the first read, so "never" does not flash).
        val dataSummary = lastExport?.let { lastExportText(it.atMillis) }
        CollapsibleSettingsSection(
            emoji = "💾",
            title = stringResource(R.string.settings_data),
            summary = dataSummary,
            expanded = dataExpanded,
            onToggle = { dataExpanded = !dataExpanded },
        ) {
            SettingsRow(
                emoji = "📤",
                title = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_desc),
                onClick = fileActions.launchExport,
                detail = dataSummary,
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
            DangerZone(hasEntries = hasEntries, onClearAll = viewModel::startClearAll)
        }

        // AI: "DeepSeek · Key：已设置 ✅", plus "笔记：发送" only while notes are being sent.
        val aiSummary = ai?.let { selection ->
            listOfNotNull(
                stringResource(selection.provider.nameRes()),
                stringResource(
                    R.string.settings_appearance_summary_item,
                    stringResource(R.string.settings_ai_summary_key),
                    stringResource(if (selection.hasKey) R.string.settings_ai_key_set else R.string.settings_ai_key_unset),
                ),
                if (aiIncludeNotes == true) stringResource(R.string.settings_ai_summary_notes_on) else null,
            ).joinToString(separator = summarySeparator)
        }
        CollapsibleSettingsSection(
            emoji = "🤖",
            title = stringResource(R.string.settings_ai),
            summary = aiSummary,
            expanded = aiExpanded,
            onToggle = { aiExpanded = !aiExpanded },
        ) {
            ai?.let { selection ->
                AiSettingsContent(
                    selection = selection,
                    includeNotes = aiIncludeNotes,
                    onSelectProvider = viewModel::selectProvider,
                    onSetKey = { showKeyDialog = true },
                    onClearKey = { showClearKeyConfirm = true },
                    onIncludeNotesChange = viewModel::setAiIncludeNotes,
                )
            }
        }

        // About
        val versionText = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
        CollapsibleSettingsSection(
            emoji = "🧮",
            title = stringResource(R.string.settings_about),
            summary = versionText,
            expanded = aboutExpanded,
            onToggle = { aboutExpanded = !aboutExpanded },
        ) {
            Text(
                text = versionText,
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

    // The AI card is only shown once the selection has loaded, so the dialogs wait for it too.
    ai?.let { selection ->
        val providerName = stringResource(selection.provider.nameRes())

        if (showKeyDialog) {
            ApiKeyDialog(
                providerName = providerName,
                onDismiss = { showKeyDialog = false },
                onSave = { key ->
                    viewModel.saveApiKey(selection.provider, key)
                    showKeyDialog = false
                },
            )
        }

        if (showClearKeyConfirm) {
            FunConfirmDialog(
                title = stringResource(R.string.settings_ai_key_clear_title, providerName),
                emoji = "🧹",
                message = stringResource(R.string.settings_ai_key_clear_message, providerName),
                confirmText = stringResource(R.string.settings_ai_key_clear_confirm),
                confirmColor = FunTheme.colors.danger,
                onConfirm = {
                    viewModel.clearApiKey(selection.provider)
                    showClearKeyConfirm = false
                },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { showClearKeyConfirm = false },
            )
        }
    }

    // Switching the edition: say clearly what changes, then confirm. Cancel stores nothing.
    pendingEdition?.let { target ->
        val targetName = stringResource(target.labelRes())
        FunConfirmDialog(
            title = stringResource(R.string.settings_edition_switch_title, targetName),
            emoji = "⚠️",
            message = stringResource(R.string.settings_edition_switch_message, targetName),
            confirmText = stringResource(R.string.settings_edition_switch_confirm),
            confirmColor = FunTheme.colors.danger,
            onConfirm = viewModel::confirmEditionSwitch,
            dismissText = stringResource(R.string.settings_edition_switch_cancel),
            onDismiss = viewModel::cancelEditionSwitch,
        )
    }

    // Delete-all flow: "are you sure" -> current PIN -> delete.
    when (clearAll.step) {
        ClearAllStep.CONFIRM -> FunConfirmDialog(
            title = stringResource(R.string.clear_all_confirm_title),
            emoji = "🧨",
            message = stringResource(R.string.clear_all_confirm_message),
            confirmText = stringResource(R.string.clear_all_confirm_next),
            confirmColor = FunTheme.colors.danger,
            onConfirm = viewModel::proceedToPin,
            dismissText = stringResource(R.string.clear_all_keep),
            onDismiss = viewModel::cancelClearAll,
        )

        ClearAllStep.PIN -> ClearAllPinDialog(
            state = clearAll,
            onConfirm = viewModel::confirmClearAll,
            onDismiss = viewModel::cancelClearAll,
        )

        ClearAllStep.HIDDEN -> Unit
    }
}

/** Danger-coloured "delete all entries" button at the bottom of the Data section. */
@Composable
private fun DangerZone(hasEntries: Boolean?, onClearAll: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    RowDivider()
    Spacer(Modifier.height(16.dp))
    ChunkyButton(
        text = stringResource(R.string.clear_all_button),
        emoji = "🧨",
        onClick = onClearAll,
        containerColor = FunTheme.colors.danger,
        enabled = hasEntries == true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (hasEntries == false) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.clear_all_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = FunTheme.colors.textSecondary,
        )
    }
}

/**
 * Second confirmation: the current 4-digit PIN (numeric keyboard, masked). The typed PIN lives only
 * in this dialog's in-memory state (plain `remember`, never saved state) and is handed to
 * [onConfirm]; wrong tries count toward the lockout just like on the lock screen.
 */
@Composable
private fun ClearAllPinDialog(
    state: ClearAllUiState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FunTheme.colors
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(state.pinResetToken) { pin = "" }

    val canSubmit = state.inputEnabled && pin.length == PinHasher.PIN_LENGTH
    val submit = { if (canSubmit) onConfirm(pin) }
    val errorText = if (state.failed && state.lockoutSeconds == 0) {
        stringResource(R.string.clear_all_failed)
    } else {
        pinMessageText(state.message, state.lockoutSeconds)
    }

    FunDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.clear_all_pin_title),
        emoji = "🔐",
        dismissible = !state.working,
        buttons = {
            ChunkyButton(
                text = stringResource(
                    if (state.working) R.string.clear_all_working else R.string.clear_all_pin_confirm,
                ),
                emoji = "🧨",
                onClick = submit,
                containerColor = colors.danger,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                text = stringResource(R.string.clear_all_keep),
                onClick = onDismiss,
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Text(
            text = stringResource(R.string.clear_all_pin_message),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        FunTextField(
            value = pin,
            onValueChange = { input -> pin = input.filter { it.isDigit() }.take(PinHasher.PIN_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.clear_all_pin_placeholder),
            leadingEmoji = "🔢",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            isError = errorText != null,
        )
        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Provider picker, then the selected provider's key, then the "send my notes too" switch.
 * [includeNotes] is `null` until read; the switch stays hidden meanwhile.
 */
@Composable
private fun AiSettingsContent(
    selection: AiSelection,
    includeNotes: Boolean?,
    onSelectProvider: (AiProvider) -> Unit,
    onSetKey: () -> Unit,
    onClearKey: () -> Unit,
    onIncludeNotesChange: (Boolean) -> Unit,
) {
    val colors = FunTheme.colors
    val provider = selection.provider
    val providerName = stringResource(provider.nameRes())

    Text(
        text = stringResource(R.string.settings_ai_provider),
        style = MaterialTheme.typography.labelLarge,
        color = colors.textSecondary,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AiProvider.entries.forEach { option ->
            ChoiceCard(
                selected = option == provider,
                onClick = { onSelectProvider(option) },
                modifier = Modifier.weight(1f),
            ) {
                EmojiText(text = option.emoji(), fontSize = 34.sp)
                Text(
                    text = stringResource(option.nameRes()),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(option.taglineRes()),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    RowDivider()
    Spacer(Modifier.height(16.dp))

    // Key
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.settings_ai_key, providerName),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (selection.hasKey) {
            StickerBadge(
                text = stringResource(R.string.settings_ai_key_set),
                containerColor = colors.mint,
                rotation = 2f,
            )
        } else {
            StickerBadge(
                text = stringResource(R.string.settings_ai_key_unset),
                containerColor = colors.surfaceAlt,
                contentColor = colors.textPrimary,
                rotation = 2f,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    ChunkyButton(
        text = stringResource(
            if (selection.hasKey) R.string.settings_ai_key_change else R.string.settings_ai_key_set_action,
        ),
        emoji = "🔑",
        onClick = onSetKey,
        modifier = Modifier.fillMaxWidth(),
    )
    if (selection.hasKey) {
        Spacer(Modifier.height(10.dp))
        ChunkyButton(
            text = stringResource(R.string.settings_ai_key_clear),
            emoji = "🧹",
            onClick = onClearKey,
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.settings_ai_note),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
    )

    if (includeNotes != null) {
        Spacer(Modifier.height(16.dp))
        RowDivider()
        Spacer(Modifier.height(6.dp))
        SettingsSwitchRow(
            emoji = "📝",
            title = stringResource(R.string.settings_ai_include_notes),
            subtitle = stringResource(R.string.settings_ai_include_notes_desc),
            checked = includeNotes,
            onToggle = onIncludeNotesChange,
            checkedTrackColor = colors.pink,
        )
    }
}

/**
 * A settings card whose body folds away. The whole header (title, arrow and, while collapsed, a
 * one-line [summary] of the current values; `null` = nothing to show yet) is one button that
 * toggles it; accessibility services hear the state ("expanded" / "collapsed") and the action
 * ("expand" / "collapse").
 */
@Composable
private fun CollapsibleSettingsSection(
    emoji: String,
    title: String,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FunTheme.colors
    val stateText = stringResource(
        if (expanded) R.string.settings_section_expanded else R.string.settings_section_collapsed,
    )
    val actionText = stringResource(
        if (expanded) R.string.settings_section_collapse else R.string.settings_section_expand,
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "collapsibleArrow",
    )

    FunCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = actionText, role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText }
                .padding(16.dp),
        ) {
            SectionHeader(
                emoji = emoji,
                title = title,
                trailing = {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.rotate(arrowRotation),
                    )
                },
            )
            AnimatedVisibility(visible = !expanded && summary != null) {
                Text(
                    text = summary.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                content = content,
            )
        }
    }
}

/** "Fingerprint unlock" switch row (only composed when the device has the hardware). */
@Composable
private fun BiometricSettingsRow(
    state: BiometricSettingUiState,
    onToggle: (Boolean) -> Unit,
) {
    val needsEnrollment = state.row == BiometricSettingRow.NEEDS_ENROLLMENT
    SettingsSwitchRow(
        emoji = "👆",
        title = stringResource(R.string.settings_biometric),
        subtitle = stringResource(
            if (needsEnrollment) R.string.settings_biometric_needs_enrollment else R.string.settings_biometric_desc,
        ),
        note = if (needsEnrollment) null else stringResource(R.string.settings_biometric_note),
        checked = state.checked,
        enabled = state.toggleEnabled,
        onToggle = onToggle,
        checkedTrackColor = FunTheme.colors.mint,
    )
}

/**
 * A row with a switch on the right; the whole row is one toggle (Switch role), so a tap anywhere
 * flips it. [note] is an optional smaller line under the [subtitle].
 */
@Composable
private fun SettingsSwitchRow(
    emoji: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    checkedTrackColor: Color,
    enabled: Boolean = true,
    note: String? = null,
) {
    val colors = FunTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onToggle,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiText(text = emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (note != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = checkedTrackColor,
                checkedBorderColor = colors.ink,
                uncheckedThumbColor = colors.ink,
                uncheckedTrackColor = colors.surfaceAlt,
                uncheckedBorderColor = colors.ink,
                disabledCheckedThumbColor = colors.onDisabled,
                disabledCheckedTrackColor = colors.disabled,
                disabledCheckedBorderColor = colors.onDisabled,
                disabledUncheckedThumbColor = colors.onDisabled,
                disabledUncheckedTrackColor = colors.disabled,
                disabledUncheckedBorderColor = colors.onDisabled,
            ),
        )
    }
}

/** Shows the confirmation prompt while turning fingerprint unlock on, and the snackbar afterwards. */
@Composable
private fun BiometricEnableEffects(viewModel: SettingsViewModel) {
    val request by viewModel.biometricPrompt.collectAsStateWithLifecycle()
    val message by viewModel.biometricMessage.collectAsStateWithLifecycle()
    val launcher = rememberBiometricPromptLauncher()
    val snackbar = LocalSnackbar.current
    val texts = BiometricPromptTexts(
        title = stringResource(R.string.lock_biometric_prompt_title),
        negativeButton = stringResource(R.string.action_cancel),
        subtitle = stringResource(R.string.settings_biometric_prompt_subtitle),
    )
    val enabledText = stringResource(R.string.settings_biometric_enabled)
    val failedText = stringResource(R.string.settings_biometric_enable_failed)

    LaunchedEffect(request) {
        val pending = request ?: return@LaunchedEffect
        viewModel.onBiometricPromptHandled(pending)
        val shown = launcher.launch(
            cipher = pending.cipher,
            texts = texts,
            confirmationRequired = true,
            onOutcome = viewModel::onBiometricPromptOutcome,
        )
        if (!shown) viewModel.onBiometricPromptOutcome(BiometricOutcome.Failed)
    }

    LaunchedEffect(message) {
        when (message) {
            BiometricSettingMessage.ENABLED -> snackbar.show(enabledText)
            BiometricSettingMessage.ENABLE_FAILED -> snackbar.show(failedText)
            null -> return@LaunchedEffect
        }
        viewModel.onBiometricMessageShown()
    }
}

@Composable
private fun SettingsRow(
    emoji: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    detail: String? = null,
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
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelLarge,
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

/** Password-style input for the selected provider's API key. The text only lives in this dialog's state. */
@Composable
private fun ApiKeyDialog(
    providerName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val toggleCd = stringResource(if (visible) R.string.settings_key_hide else R.string.settings_key_show)

    FunDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_key_dialog_title, providerName),
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
            text = stringResource(R.string.settings_key_dialog_body, providerName),
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
