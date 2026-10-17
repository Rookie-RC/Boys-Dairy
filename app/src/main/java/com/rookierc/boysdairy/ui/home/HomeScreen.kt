package com.rookierc.boysdairy.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.buildDaySummaries
import com.rookierc.boysdairy.domain.computeStats
import com.rookierc.boysdairy.domain.groupEntriesByDate
import com.rookierc.boysdairy.domain.shouldRemindBackup
import com.rookierc.boysdairy.ui.common.ChunkyButton
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.ScreenTitle
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.StickerBadge
import com.rookierc.boysdairy.ui.common.lastExportText
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.common.rememberFormatter
import com.rookierc.boysdairy.ui.common.rememberToday
import com.rookierc.boysdairy.ui.theme.FunColors
import com.rookierc.boysdairy.ui.theme.FunTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * @param onOpenInsights opens the full report
 * @param onEditEntry open an entry (tapped in the calendar's day sheet) for editing
 * @param onAddForDate log a new entry back-filled for a day picked in the calendar
 * @param onBackUpNow "back up now" on the backup reminder card (opens Settings)
 *
 * Navigation stays in MainNavigation.
 */
@Composable
fun HomeScreen(
    onOpenInsights: () -> Unit,
    onEditEntry: (entryId: Long) -> Unit,
    onAddForDate: (LocalDate) -> Unit,
    onBackUpNow: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today by rememberToday()

    val entries = state.entries
    val stats = remember(entries, today) {
        computeStats(entries, System.currentTimeMillis(), ZoneId.systemDefault())
    }
    val daySummaries = remember(entries) { buildDaySummaries(entries, ZoneId.systemDefault()) }
    val entriesByDay = remember(entries) { groupEntriesByDate(entries, ZoneId.systemDefault()) }
    val lastEntry = remember(entries) { entries.maxByOrNull { it.timestamp } }
    val remindBackup = remember(entries.size, state.lastExportAt, state.backupReminderSnoozedUntil, today) {
        shouldRemindBackup(
            entryCount = entries.size,
            lastExportAt = state.lastExportAt,
            snoozedUntil = state.backupReminderSnoozedUntil,
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        )
    }

    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(
            emoji = "📒",
            title = stringResource(R.string.home_title),
            subtitle = stringResource(R.string.home_subtitle),
        )

        if (!state.isLoading) {
            CooldownCard(
                daysSince = stats.daysSinceLast,
                lastEntry = lastEntry,
                today = today,
            )

            StreakLevel.of(stats.consecutiveActiveDays)?.let { level ->
                StreakCard(level = level, days = stats.consecutiveActiveDays)
            }

            StatsRow(
                last7Days = stats.last7DaysCount,
                last30Days = stats.last30DaysCount,
                averagePleasure = stats.averagePleasure,
            )

            if (remindBackup) {
                BackupReminderCard(
                    entryCount = entries.size,
                    lastExportAt = state.lastExportAt,
                    onBackUpNow = onBackUpNow,
                    onSnooze = viewModel::snoozeBackupReminder,
                )
            }

            if (entries.isNotEmpty()) {
                ChunkyButton(
                    text = stringResource(R.string.insights_open),
                    emoji = "📊",
                    onClick = onOpenInsights,
                    containerColor = FunTheme.colors.grape,
                    contentColor = FunTheme.colors.onGrape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        TipCard(today = today)

        CalendarCard(
            month = state.visibleMonth,
            days = daySummaries,
            today = today,
            onPreviousMonth = viewModel::showPreviousMonth,
            onNextMonth = viewModel::showNextMonth,
            onCurrentMonth = { viewModel.showMonth(YearMonth.from(today)) },
            onDayClick = { day -> selectedDay = day },
        )
    }

    selectedDay?.let { day ->
        DayEntriesSheet(
            date = day,
            entries = entriesByDay[day].orEmpty().sortedBy(Entry::timestamp),
            onDismiss = { selectedDay = null },
            onEntryClick = { entry ->
                selectedDay = null
                onEditEntry(entry.id)
            },
            // No back-filling into the future (today itself is fine).
            onAddForDay = if (day.isAfter(today)) {
                null
            } else {
                {
                    selectedDay = null
                    onAddForDate(day)
                }
            },
        )
    }
}

/** Fill / text colour pair of the cooldown card for each tier. */
private fun FunColors.cooldownFill(tier: CooldownTier): Pair<Color, Color> = when (tier) {
    CooldownTier.NONE -> lemon to onAccent
    CooldownTier.FRESH -> grape to onGrape
    CooldownTier.RECOVERING -> pink to onAccent
    CooldownTier.RELOADING -> lemon to onAccent
    CooldownTier.ASCETIC -> solo to onAccent
    CooldownTier.SERENE -> mint to onAccent
    CooldownTier.MONK -> grape to onGrape
}

@Composable
private fun CooldownCard(daysSince: Long?, lastEntry: Entry?, today: LocalDate) {
    val colors = FunTheme.colors
    val tier = CooldownTier.of(daysSince)
    val (fill, onFill) = colors.cooldownFill(tier)

    val subtitles = stringArrayResource(tier.subtitlesRes)
    val subtitle = subtitles[pickByDay(today.toEpochDay(), subtitles.size)]
    val days = daysSince?.toInt() ?: 0
    val unit = quantityString(R.plurals.home_days_unit, days, days)
    val dateFormatter = rememberFormatter(R.string.pattern_date_weekday)

    FunCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = fill,
        contentColor = onFill,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_cooldown_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (daysSince == null) "∞" else days.toString(),
                        fontSize = 76.sp,
                        lineHeight = 84.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
                Text(
                    text = stringResource(tier.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(8.dp))
            EmojiText(text = tier.emoji, fontSize = 56.sp)
        }
        if (lastEntry != null) {
            Spacer(Modifier.height(12.dp))
            val lastDate = Instant.ofEpochMilli(lastEntry.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(dateFormatter)
            Text(
                text = stringResource(R.string.home_last_entry, lastDate),
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.home_empty_hint),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun StreakCard(level: StreakLevel, days: Int) {
    val colors = FunTheme.colors
    FunCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = colors.pinkTint,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiText(text = level.emoji, fontSize = 40.sp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(level.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(level.subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        StickerBadge(
            text = quantityString(R.plurals.streak_days_badge, days, days),
            emoji = "🔥",
            containerColor = colors.pink,
        )
    }
}

@Composable
private fun StatsRow(last7Days: Int, last30Days: Int, averagePleasure: Double?) {
    val colors = FunTheme.colors
    val locale: Locale = LocalConfiguration.current.locales.get(0)
    val pleasureText = averagePleasure?.let { String.format(locale, "%.1f", it) }
        ?: stringResource(R.string.stats_no_value)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            emoji = "📅",
            value = last7Days.toString(),
            label = stringResource(R.string.stats_last7),
            containerColor = colors.soloTint,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            emoji = "🗓️",
            value = last30Days.toString(),
            label = stringResource(R.string.stats_last30),
            containerColor = colors.grapeTint,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            emoji = "🎆",
            value = pleasureText,
            label = stringResource(R.string.stats_avg_pleasure),
            containerColor = colors.lemonTint,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    emoji: String,
    value: String,
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    FunCard(
        modifier = modifier,
        containerColor = containerColor,
        shadowOffset = 3.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
            vertical = 12.dp,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmojiText(text = emoji, fontSize = 22.sp)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Diary lives only on this phone: nudge towards an export. Shown per [shouldRemindBackup]. */
@Composable
private fun BackupReminderCard(
    entryCount: Int,
    lastExportAt: Long?,
    onBackUpNow: () -> Unit,
    onSnooze: () -> Unit,
) {
    val colors = FunTheme.colors
    FunCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = colors.lemonTint,
    ) {
        SectionHeader(emoji = "💾", title = stringResource(R.string.backup_reminder_title))
        Spacer(Modifier.height(8.dp))
        Text(
            text = quantityString(R.plurals.backup_reminder_body, entryCount, entryCount),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = lastExportText(lastExportAt),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(12.dp))
        ChunkyButton(
            text = stringResource(R.string.backup_reminder_go),
            emoji = "📤",
            onClick = onBackUpNow,
            containerColor = colors.lemon,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        ChunkyButton(
            text = stringResource(R.string.backup_reminder_snooze),
            emoji = "😴",
            onClick = onSnooze,
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TipCard(today: LocalDate) {
    val tips = stringArrayResource(R.array.daily_tips)
    val tip = tips[pickByDay(today.toEpochDay(), tips.size)]
    FunCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = FunTheme.colors.mintTint,
    ) {
        SectionHeader(emoji = "🍵", title = stringResource(R.string.tip_title))
        Spacer(Modifier.height(8.dp))
        Text(text = tip, style = MaterialTheme.typography.bodyLarge)
    }
}
