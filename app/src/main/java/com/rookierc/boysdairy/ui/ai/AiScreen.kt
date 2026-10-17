package com.rookierc.boysdairy.ui.ai

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.ai.AiError
import com.rookierc.boysdairy.ai.AiProvider
import com.rookierc.boysdairy.ai.AiSelection
import com.rookierc.boysdairy.ai.RoastLevel
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.ChoiceCard
import com.rookierc.boysdairy.ui.common.ChunkyButton
import com.rookierc.boysdairy.ui.common.EmojiChip
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.LocalSnackbar
import com.rookierc.boysdairy.ui.common.ScreenTitle
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.emoji
import com.rookierc.boysdairy.ui.common.nameRes
import com.rookierc.boysdairy.ui.common.stringArrayResource
import com.rookierc.boysdairy.ui.common.stringResource
import com.rookierc.boysdairy.ui.theme.FunTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * "AI 锐评" tab: who answers, how harsh, and the big button. Nothing is sent anywhere until that
 * button is tapped. Whether notes go along is a switch in Settings (off by default), not shown here.
 *
 * @param onOpenAiKeySettings opens Settings with the API-key dialog
 */
@Composable
fun AiScreen(
    onOpenAiKeySettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiViewModel = viewModel(factory = AiViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val languageTag = LocalConfiguration.current.locales.get(0).toLanguageTag()
    val scrollState = rememberScrollState()
    val colors = FunTheme.colors
    val providerOrDefault = selection?.provider ?: AiProvider.DEFAULT
    val providerName = stringResource(providerOrDefault.nameRes())

    // Bring a fresh result / error into view.
    val phaseKey = when (state.phase) {
        AiPhase.Idle -> 0
        AiPhase.Loading -> 1
        is AiPhase.Result -> 2
        is AiPhase.Failed -> 3
    }
    LaunchedEffect(phaseKey) {
        if (phaseKey >= 1) {
            delay(150)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(
            emoji = "🤖",
            title = stringResource(R.string.ai_title),
            subtitle = stringResource(R.string.ai_subtitle),
        )

        // Who answers: a shortcut for the setting that lives in Settings.
        ProviderCard(
            selection = selection,
            onSelect = viewModel::setProvider,
        )

        // Roast level
        FunCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(emoji = "🌶️", title = stringResource(R.string.ai_roast_label))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoastChoice(
                    emoji = "🧸",
                    title = stringResource(R.string.ai_roast_gentle),
                    hint = stringResource(R.string.ai_roast_gentle_hint),
                    selected = state.roastLevel == RoastLevel.GENTLE,
                    selectedColor = colors.mint,
                    onClick = { viewModel.setRoastLevel(RoastLevel.GENTLE) },
                    modifier = Modifier.weight(1f),
                )
                RoastChoice(
                    emoji = "🌶️",
                    title = stringResource(R.string.ai_roast_spicy),
                    hint = stringResource(R.string.ai_roast_spicy_hint),
                    selected = state.roastLevel == RoastLevel.SPICY,
                    selectedColor = colors.pink,
                    onClick = { viewModel.setRoastLevel(RoastLevel.SPICY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (selection?.hasKey == false) {
            FunCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = colors.lemonTint,
            ) {
                SectionHeader(emoji = "🔑", title = stringResource(R.string.ai_no_key_title, providerName))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.ai_no_key_body, providerName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                ChunkyButton(
                    text = stringResource(R.string.ai_go_set_key),
                    emoji = "🔑",
                    onClick = onOpenAiKeySettings,
                    containerColor = colors.lemon,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when (val phase = state.phase) {
            AiPhase.Idle -> {
                ChunkyButton(
                    text = stringResource(R.string.ai_run),
                    emoji = "🔥",
                    onClick = { viewModel.analyze(languageTag) },
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 64.dp,
                )
                Text(
                    text = stringResource(R.string.ai_privacy_footer, providerName),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AiPhase.Loading -> LoadingCard(onCancel = viewModel::cancel)

            is AiPhase.Result -> ResultCard(
                text = phase.text,
                onAgain = { viewModel.analyze(languageTag) },
                onDone = viewModel::reset,
            )

            is AiPhase.Failed -> ErrorCard(
                error = phase.error,
                providerName = stringResource(phase.provider.nameRes()),
                onRetry = { viewModel.analyze(languageTag) },
                onOpenAiKeySettings = onOpenAiKeySettings,
                onDismiss = viewModel::reset,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** Two chips to switch provider; the selected chip is the one the next roast will use. */
@Composable
private fun ProviderCard(
    selection: AiSelection?,
    onSelect: (AiProvider) -> Unit,
) {
    FunCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(emoji = "🎤", title = stringResource(R.string.ai_provider_label))
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiProvider.entries.forEach { provider ->
                EmojiChip(
                    label = stringResource(provider.nameRes()),
                    emoji = provider.emoji(),
                    selected = selection?.provider == provider,
                    onClick = { onSelect(provider) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RoastChoice(
    emoji: String,
    title: String,
    hint: String,
    selected: Boolean,
    selectedColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChoiceCard(
        selected = selected,
        onClick = onClick,
        selectedColor = selectedColor,
        modifier = modifier,
    ) {
        EmojiText(text = emoji, fontSize = 34.sp)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingCard(onCancel: () -> Unit) {
    val colors = FunTheme.colors
    val messages = stringArrayResource(R.array.ai_loading_messages)
    var index by remember { mutableIntStateOf(Random.nextInt(messages.size)) }
    LaunchedEffect(messages.size) {
        while (true) {
            delay(1_800)
            index = (index + 1) % messages.size
        }
    }
    val transition = rememberInfiniteTransition(label = "aiBounce")
    val bounce by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aiBounceValue",
    )

    FunCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = colors.lemon,
        contentColor = colors.onAccent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🤖",
                fontSize = 48.sp,
                modifier = Modifier.offset(y = bounce.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = messages[index % messages.size],
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        ChunkyButton(
            text = stringResource(R.string.ai_cancel),
            onClick = onCancel,
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultCard(
    text: String,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = FunTheme.colors
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val copiedMessage = stringResource(R.string.ai_copied)
    FunCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(emoji = "🔥", title = stringResource(R.string.ai_result_title))
        Spacer(Modifier.height(12.dp))
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ai_result_disclaimer),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        ChunkyButton(
            text = stringResource(R.string.ai_copy),
            emoji = "📋",
            onClick = {
                // Android 13+ shows its own "copied" confirmation; only older versions need ours.
                if (copySensitiveText(context, text) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    snackbar.show(copiedMessage)
                }
            },
            containerColor = colors.mint,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        ChunkyButton(
            text = stringResource(R.string.ai_again),
            emoji = "🔁",
            onClick = onAgain,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        ChunkyButton(
            text = stringResource(R.string.ai_done),
            onClick = onDone,
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Puts [text] on the system clipboard, flagged as sensitive so Android 13+ does not show it in the
 * clipboard preview (Compose's clipboard API cannot set this extra). The clip has no label, so
 * nothing about the app shows up next to it. Returns false if there is no clipboard service.
 */
private fun copySensitiveText(context: Context, text: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    val clip = ClipData.newPlainText(null, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    return true
}

@Composable
private fun ErrorCard(
    error: AiError,
    providerName: String,
    onRetry: () -> Unit,
    onOpenAiKeySettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FunTheme.colors
    val needsKey = error == AiError.NO_API_KEY || error == AiError.INVALID_API_KEY

    FunCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = colors.pinkTint,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiText(text = error.emoji(), fontSize = 40.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = error.message(providerName),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        if (needsKey) {
            ChunkyButton(
                text = stringResource(R.string.ai_go_set_key),
                emoji = "🔑",
                onClick = onOpenAiKeySettings,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (error != AiError.NO_DATA) {
            ChunkyButton(
                text = stringResource(R.string.ai_retry),
                emoji = "🔁",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        ChunkyButton(
            text = stringResource(R.string.ai_done),
            onClick = onDismiss,
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The localized message; the key- and request-related ones name the provider that was asked. */
@Composable
private fun AiError.message(providerName: String): String = when (this) {
    AiError.NO_API_KEY -> stringResource(R.string.ai_error_no_key, providerName)
    AiError.NO_DATA -> stringResource(R.string.ai_error_no_data)
    AiError.INVALID_API_KEY -> stringResource(R.string.ai_error_invalid_key, providerName)
    AiError.INSUFFICIENT_BALANCE -> stringResource(R.string.ai_error_insufficient_balance)
    AiError.BAD_REQUEST -> stringResource(R.string.ai_error_bad_request, providerName)
    AiError.RATE_LIMITED -> stringResource(R.string.ai_error_rate_limited)
    AiError.OVERLOADED -> stringResource(R.string.ai_error_overloaded)
    AiError.NETWORK -> stringResource(R.string.ai_error_network)
    AiError.REFUSED -> stringResource(R.string.ai_error_refused)
    AiError.SERVER -> stringResource(R.string.ai_error_server)
    AiError.UNKNOWN -> stringResource(R.string.ai_error_unknown)
}

private fun AiError.emoji(): String = when (this) {
    AiError.NO_API_KEY -> "🔑"
    AiError.NO_DATA -> "📭"
    AiError.INVALID_API_KEY -> "🔐"
    AiError.INSUFFICIENT_BALANCE -> "💸"
    AiError.BAD_REQUEST -> "🔍"
    AiError.RATE_LIMITED -> "⏳"
    AiError.OVERLOADED -> "🚇"
    AiError.NETWORK -> "📡"
    AiError.REFUSED -> "🙊"
    AiError.SERVER -> "🛠️"
    AiError.UNKNOWN -> "🌀"
}
