@file:OptIn(ExperimentalMaterial3Api::class)

package com.rookierc.boysdairy.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.BuildConfig
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.data.ThemeMode
import com.rookierc.boysdairy.ui.common.NoInsets
import com.rookierc.boysdairy.ui.common.SectionLabel

private enum class SettingsDialog { THEME, LANGUAGE }

@Composable
fun SettingsScreen(
    onChangePinClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var language by remember { mutableStateOf(currentAppLanguage()) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
        contentWindowInsets = NoInsets,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            GroupHeader(R.string.settings_security)
            SettingsRow(
                icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                title = stringResource(R.string.settings_change_pin),
                subtitle = stringResource(R.string.settings_change_pin_desc),
                onClick = onChangePinClick,
            )

            GroupHeader(R.string.settings_appearance)
            SettingsRow(
                icon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(themeMode.labelRes()),
                onClick = { dialog = SettingsDialog.THEME },
            )
            SettingsRow(
                icon = { Icon(Icons.Filled.Language, contentDescription = null) },
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(language.labelRes()),
                onClick = { dialog = SettingsDialog.LANGUAGE },
            )

            GroupHeader(R.string.settings_about)
            SettingsRow(
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                subtitle = null,
                onClick = null,
            )
        }
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            titleRes = R.string.settings_theme,
            options = ThemeMode.entries,
            selected = themeMode,
            labelRes = { it.labelRes() },
            onSelect = {
                viewModel.setThemeMode(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.LANGUAGE -> ChoiceDialog(
            titleRes = R.string.settings_language,
            options = AppLanguage.entries,
            selected = language,
            labelRes = { it.labelRes() },
            onSelect = {
                dialog = null
                if (it != language) {
                    language = it
                    applyAppLanguage(it)
                }
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.CHINESE -> R.string.language_chinese
    AppLanguage.ENGLISH -> R.string.language_english
}

@Composable
private fun GroupHeader(@StringRes textRes: Int) {
    SectionLabel(
        text = stringResource(textRes),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { text -> { Text(text) } },
        leadingContent = icon,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun <T> ChoiceDialog(
    @StringRes titleRes: Int,
    options: List<T>,
    selected: T,
    labelRes: (T) -> Int,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = { onSelect(option) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(labelRes(option)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
