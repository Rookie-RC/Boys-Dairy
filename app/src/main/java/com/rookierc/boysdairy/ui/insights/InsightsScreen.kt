package com.rookierc.boysdairy.ui.insights

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.AchievementProgress
import com.rookierc.boysdairy.domain.EntryStats
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.HourBucket
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.domain.WeekBucket
import com.rookierc.boysdairy.domain.busiestKeys
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.EmptyState
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.FunTopBar
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.StickerBadge
import com.rookierc.boysdairy.ui.common.emoji
import com.rookierc.boysdairy.ui.common.labelRes
import com.rookierc.boysdairy.ui.common.labels
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.common.rememberFormatter
import com.rookierc.boysdairy.ui.common.rememberToday
import com.rookierc.boysdairy.ui.theme.FunColors
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme
import com.rookierc.boysdairy.ui.theme.accent
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

/** "完整战报": the whole diary at a glance. Full screen, reached from the home screen. */
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val today by rememberToday()

    val loaded = entries
    val report = remember(loaded, today) {
        if (loaded.isNullOrEmpty()) {
            null
        } else {
            InsightsReport.compute(loaded, System.currentTimeMillis(), ZoneId.systemDefault())
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        FunTopBar(
            title = stringResource(R.string.insights_title),
            emoji = "📊",
            onBack = onBack,
        )

        when {
            loaded == null -> Box(Modifier.fillMaxSize())

            report == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    emoji = "📭",
                    message = stringResource(R.string.insights_empty),
                    hint = stringResource(R.string.insights_empty_hint),
                )
            }

            else -> InsightsContent(
                report = report,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InsightsContent(report: InsightsReport, modifier: Modifier = Modifier) {
    val colors = FunTheme.colors
    val locale: Locale = LocalConfiguration.current.locales.get(0)

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OverviewCard(report = report, locale = locale)

        WeeklyChartCard(weeks = report.weeks, locale = locale)

        val busiestDays = busiestKeys(report.weekdays)
        DistributionCard(
            emoji = "📆",
            title = stringResource(R.string.insights_weekday_title),
            rows = report.weekdays.map { (day, count) ->
                BarRow(
                    label = day.getDisplayName(JavaTextStyle.SHORT, locale),
                    count = count,
                    highlighted = day in busiestDays,
                )
            },
            labelWidth = 56.dp,
            caption = stringResource(R.string.insights_busiest_hint),
        )

        val busiestBuckets = busiestKeys(report.hourBuckets)
        DistributionCard(
            emoji = "🕰️",
            title = stringResource(R.string.insights_hour_title),
            rows = report.hourBuckets.map { (bucket, count) ->
                BarRow(
                    label = stringResource(bucket.labelRes()),
                    emoji = bucket.emoji(),
                    count = count,
                    highlighted = bucket in busiestBuckets,
                )
            },
            labelWidth = 132.dp,
        )

        MoodCard(stats = report.stats)

        AchievementWall(achievements = report.achievements)

        Text(
            text = stringResource(R.string.insights_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ---- Overview ---------------------------------------------------------------------------------

@Composable
private fun OverviewCard(report: InsightsReport, locale: Locale) {
    val colors = FunTheme.colors
    val stats = report.stats
    val noValue = stringResource(R.string.stats_no_value)
    val totalText = remember(stats.totalCount, locale) {
        NumberFormat.getIntegerInstance(locale).format(stats.totalCount)
    }

    FunCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(emoji = "🧾", title = stringResource(R.string.insights_overview))
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.insights_total_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = totalText,
                        fontSize = 56.sp,
                        lineHeight = 62.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = quantityString(R.plurals.insights_times_unit, stats.totalCount),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            EmojiText(text = "🏆", fontSize = 44.sp)
        }

        Spacer(Modifier.height(8.dp))
        ShareBar(solo = stats.soloCount, partner = stats.partnerCount)
        Spacer(Modifier.height(8.dp))
        ShareLegend(solo = stats.soloCount, partner = stats.partnerCount, locale = locale)

        Spacer(Modifier.height(16.dp))
        val tiles = listOf(
            MiniStatData(
                emoji = "⏱️",
                value = stats.averageDurationMinutes
                    ?.let { stringResource(R.string.insights_minutes_value, it.oneDecimal(locale)) }
                    ?: noValue,
                label = stringResource(R.string.insights_avg_duration),
                containerColor = colors.soloTint,
            ),
            MiniStatData(
                emoji = "🎆",
                value = stats.averagePleasure
                    ?.let { stringResource(R.string.insights_pleasure_value, it.oneDecimal(locale)) }
                    ?: noValue,
                label = stringResource(R.string.stats_avg_pleasure),
                containerColor = colors.lemonTint,
            ),
            MiniStatData(
                emoji = "📏",
                value = stats.averageIntervalDays
                    ?.let { stringResource(R.string.insights_days_decimal, it.oneDecimal(locale)) }
                    ?: noValue,
                label = stringResource(R.string.insights_avg_interval),
                containerColor = colors.mintTint,
            ),
            MiniStatData(
                emoji = "🧘",
                value = stats.longestGapDays?.let { gap ->
                    val days = gap.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    quantityString(R.plurals.insights_days_value, days, days)
                } ?: noValue,
                label = stringResource(R.string.insights_longest_gap),
                containerColor = colors.grapeTint,
            ),
            MiniStatData(
                emoji = "🔥",
                value = quantityString(
                    R.plurals.insights_days_value,
                    stats.consecutiveActiveDays,
                    stats.consecutiveActiveDays,
                ),
                label = stringResource(R.string.insights_current_streak),
                containerColor = colors.pinkTint,
            ),
            MiniStatData(
                emoji = "🏅",
                value = quantityString(R.plurals.insights_days_value, report.longestStreak, report.longestStreak),
                label = stringResource(R.string.insights_longest_streak),
                containerColor = colors.surfaceAlt,
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { tile ->
                        MiniStat(
                            data = tile,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.insights_overview_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

/** Solo / partner split as one chunky segmented bar. */
@Composable
private fun ShareBar(solo: Int, partner: Int) {
    val colors = FunTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(FunShapes.chip)
            .background(colors.surfaceAlt)
            .border(width = 2.dp, color = colors.ink, shape = FunShapes.chip),
    ) {
        if (solo > 0) {
            Box(
                Modifier
                    .weight(solo.toFloat())
                    .fillMaxHeight()
                    .background(EntryType.SOLO.accent()),
            )
        }
        if (solo > 0 && partner > 0) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(colors.ink),
            )
        }
        if (partner > 0) {
            Box(
                Modifier
                    .weight(partner.toFloat())
                    .fillMaxHeight()
                    .background(EntryType.PARTNER.accent()),
            )
        }
    }
}

@Composable
private fun ShareLegend(solo: Int, partner: Int, locale: Locale) {
    val total = (solo + partner).coerceAtLeast(1)
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(EntryType.SOLO to solo, EntryType.PARTNER to partner).forEach { (type, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendSwatch(color = type.accent())
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.insights_share_line,
                        stringResource(type.labelRes()),
                        count,
                        percent.format(count.toDouble() / total),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = FunTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(
        Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .border(width = 1.5.dp, color = FunTheme.colors.ink, shape = RoundedCornerShape(4.dp)),
    )
}

private class MiniStatData(
    val emoji: String,
    val value: String,
    val label: String,
    val containerColor: Color,
)

/** Small tinted tile: text on tints is always textPrimary (FunCard's default). */
@Composable
private fun MiniStat(data: MiniStatData, modifier: Modifier = Modifier) {
    FunCard(
        modifier = modifier.semantics(mergeDescendants = true) {},
        containerColor = data.containerColor,
        shadowOffset = 3.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiText(text = data.emoji, fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(text = data.label, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(text = data.value, style = MaterialTheme.typography.titleLarge)
    }
}

// ---- Weekly chart -----------------------------------------------------------------------------

@Composable
private fun WeeklyChartCard(weeks: List<WeekBucket>, locale: Locale) {
    val colors = FunTheme.colors
    val soloColor = EntryType.SOLO.accent()
    val partnerColor = EntryType.PARTNER.accent()
    val maxTotal = weeks.maxOfOrNull { it.total } ?: 0

    FunCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            emoji = "📈",
            title = quantityString(R.plurals.insights_weekly_title, weeks.size, weeks.size),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(color = soloColor, label = stringResource(R.string.home_legend_solo))
            LegendItem(color = partnerColor, label = stringResource(R.string.home_legend_partner))
        }
        Spacer(Modifier.height(12.dp))

        if (maxTotal == 0) {
            Text(
                text = stringResource(R.string.insights_weekly_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = quantityString(R.plurals.insights_weekly_peak, maxTotal, maxTotal),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))

            val longDate = remember(locale) {
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            }
            val busiest = weeks.last { it.total == maxTotal }
            val description = stringResource(
                R.string.insights_weekly_cd,
                weeks.size,
                weeks.sumOf { it.total },
                weeks.sumOf { it.solo },
                weeks.sumOf { it.partner },
                busiest.weekStart.format(longDate),
                maxTotal,
            )
            Column(modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
                WeeklyBars(
                    weeks = weeks,
                    maxTotal = maxTotal,
                    soloColor = soloColor,
                    partnerColor = partnerColor,
                    colors = colors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
                Spacer(Modifier.height(6.dp))
                WeekAxisLabels(weeks = weeks)
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendSwatch(color = color)
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Stacked weekly bars (solo at the bottom, partner on top), outlined in ink with a hard offset
 * shadow; the current week's slot gets a quiet highlight.
 */
@Composable
private fun WeeklyBars(
    weeks: List<WeekBucket>,
    maxTotal: Int,
    soloColor: Color,
    partnerColor: Color,
    colors: FunColors,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val count = weeks.size
        if (count == 0 || maxTotal <= 0) return@Canvas

        val stroke = 2.dp.toPx()
        val shadowOffset = 3.dp.toPx()
        val radius = 6.dp.toPx()
        val slot = size.width / count
        val barWidth = slot * 0.62f
        val baseline = size.height - stroke / 2f
        val top = stroke
        val usable = baseline - top

        // Current week highlight.
        drawRoundRect(
            color = colors.surfaceAlt,
            topLeft = Offset((count - 1) * slot + 1.dp.toPx(), 0f),
            size = Size(slot - 2.dp.toPx(), baseline),
            cornerRadius = CornerRadius(radius),
        )

        // Peak guide line.
        drawLine(
            color = colors.ink.copy(alpha = 0.3f),
            start = Offset(0f, top),
            end = Offset(size.width, top),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )

        weeks.forEachIndexed { index, week ->
            if (week.total == 0) return@forEachIndexed
            val left = index * slot + (slot - barWidth) / 2f
            val barHeight = usable * week.total / maxTotal
            val barTop = baseline - barHeight
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, barTop, left + barWidth, baseline),
                        topLeft = CornerRadius(radius),
                        topRight = CornerRadius(radius),
                        bottomRight = CornerRadius.Zero,
                        bottomLeft = CornerRadius.Zero,
                    ),
                )
            }

            // Hard shadow, cut off at the baseline.
            clipRect(bottom = baseline) {
                translate(left = shadowOffset, top = shadowOffset) {
                    drawPath(path = path, color = colors.shadow)
                }
            }

            val soloHeight = barHeight * week.solo / week.total
            clipPath(path) {
                drawRect(
                    color = partnerColor,
                    topLeft = Offset(left, barTop),
                    size = Size(barWidth, barHeight - soloHeight),
                )
                drawRect(
                    color = soloColor,
                    topLeft = Offset(left, baseline - soloHeight),
                    size = Size(barWidth, soloHeight),
                )
                if (week.solo > 0 && week.partner > 0) {
                    drawLine(
                        color = colors.ink,
                        start = Offset(left, baseline - soloHeight),
                        end = Offset(left + barWidth, baseline - soloHeight),
                        strokeWidth = stroke,
                    )
                }
            }
            drawPath(path = path, color = colors.ink, style = Stroke(width = stroke))
        }

        drawLine(
            color = colors.ink,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Dates under a few bars only (every fourth week) plus "this week" under the last one. */
@Composable
private fun WeekAxisLabels(weeks: List<WeekBucket>) {
    val formatter = rememberFormatter(R.string.pattern_insights_week)
    val thisWeek = stringResource(R.string.insights_this_week)
    val color = FunTheme.colors.textSecondary

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val slot: Dp = maxWidth / weeks.size
        weeks.forEachIndexed { index, week ->
            val isLast = index == weeks.lastIndex
            if (isLast || index % AXIS_LABEL_EVERY == 0) {
                val align = when {
                    isLast -> Alignment.End
                    index == 0 -> Alignment.Start
                    else -> Alignment.CenterHorizontally
                }
                Box(
                    modifier = Modifier
                        .offset(x = slot * index)
                        .width(slot),
                ) {
                    Text(
                        text = if (isLast) thisWeek else week.weekStart.format(formatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(align = align, unbounded = true),
                    )
                }
            }
        }
    }
}

private const val AXIS_LABEL_EVERY = 4

// ---- Distributions ----------------------------------------------------------------------------

private class BarRow(
    val label: String,
    val count: Int,
    val highlighted: Boolean,
    val emoji: String? = null,
)

@Composable
private fun DistributionCard(
    emoji: String,
    title: String,
    rows: List<BarRow>,
    labelWidth: Dp,
    caption: String? = null,
) {
    FunCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(emoji = emoji, title = title)
        Spacer(Modifier.height(12.dp))
        DistributionBars(rows = rows, labelWidth = labelWidth)
        if (caption != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = FunTheme.colors.textSecondary,
            )
        }
    }
}

/** Horizontal bars, one per row; the busiest rows are lemon and wear a crown. */
@Composable
private fun DistributionBars(rows: List<BarRow>, labelWidth: Dp) {
    val colors = FunTheme.colors
    val max = (rows.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val busiestState = stringResource(R.string.insights_busiest_state)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        if (row.highlighted) stateDescription = busiestState
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.width(labelWidth),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.emoji != null) {
                        EmojiText(text = row.emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                BarTrack(
                    fraction = row.count.toFloat() / max,
                    color = if (row.highlighted) colors.lemon else colors.grape,
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = row.count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 24.dp),
                )
                Box(
                    modifier = Modifier.width(26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (row.highlighted) EmojiText(text = "👑", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun BarTrack(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val colors = FunTheme.colors
    Box(
        modifier = modifier
            .clip(FunShapes.chip)
            .background(colors.surfaceAlt)
            .border(width = 2.dp, color = colors.ink, shape = FunShapes.chip),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(MIN_BAR_FRACTION, 1f))
                    .clip(FunShapes.chip)
                    .background(color)
                    .border(width = 2.dp, color = colors.ink, shape = FunShapes.chip),
            )
        }
    }
}

/** Keeps a bar with a non-zero count visible as a small pill. */
private const val MIN_BAR_FRACTION = 0.08f

@StringRes
private fun HourBucket.labelRes(): Int = when (this) {
    HourBucket.NIGHT -> R.string.insights_hour_night
    HourBucket.MORNING -> R.string.insights_hour_morning
    HourBucket.AFTERNOON -> R.string.insights_hour_afternoon
    HourBucket.EVENING -> R.string.insights_hour_evening
}

private fun HourBucket.emoji(): String = when (this) {
    HourBucket.NIGHT -> "🌙"
    HourBucket.MORNING -> "🌅"
    HourBucket.AFTERNOON -> "☀️"
    HourBucket.EVENING -> "🌆"
}

// ---- Moods ------------------------------------------------------------------------------------

@Composable
private fun MoodCard(stats: EntryStats) {
    FunCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(emoji = "🎭", title = stringResource(R.string.insights_mood_title))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.add_mood_before_label),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        DistributionBars(rows = moodRows(stats.moodBeforeCounts), labelWidth = 116.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.add_mood_after_label),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        DistributionBars(rows = moodRows(stats.moodAfterCounts), labelWidth = 116.dp)
    }
}

/** Most frequent first; ties keep the mood declaration order. */
@Composable
private fun moodRows(counts: Map<Mood, Int>): List<BarRow> {
    val top = busiestKeys(counts)
    return counts.entries
        .sortedByDescending { it.value }
        .map { (mood, count) ->
            BarRow(
                label = stringResource(mood.labelRes()),
                emoji = mood.emoji(),
                count = count,
                highlighted = mood in top,
            )
        }
}

// ---- Achievements -----------------------------------------------------------------------------

@Composable
private fun AchievementWall(achievements: List<AchievementProgress>) {
    val colors = FunTheme.colors
    val unlockedCount = achievements.count { it.unlocked }

    FunCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            emoji = "🏆",
            title = stringResource(R.string.insights_achievements_title),
            trailing = {
                StickerBadge(
                    text = stringResource(R.string.insights_achievements_count, unlockedCount, achievements.size),
                    containerColor = colors.lemon,
                    rotation = 2f,
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            achievements.chunked(2).forEachIndexed { rowIndex, pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEachIndexed { column, item ->
                        AchievementSticker(
                            item = item,
                            index = rowIndex * 2 + column,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Unlocked: a tilted, coloured sticker. Locked: flat grey with `progress/target` and a small bar. */
@Composable
private fun AchievementSticker(item: AchievementProgress, index: Int, modifier: Modifier = Modifier) {
    val colors = FunTheme.colors
    val ui = item.achievement.labels()
    val (fill, onFill) = if (item.unlocked) colors.stickerFill(index) else colors.disabled to colors.textPrimary
    val secondaryText = if (item.unlocked) onFill else colors.textSecondary

    FunCard(
        modifier = modifier
            .rotate(if (item.unlocked) STICKER_TILTS[index % STICKER_TILTS.size] else 0f)
            .semantics(mergeDescendants = true) {},
        containerColor = fill,
        contentColor = onFill,
        shadowOffset = if (item.unlocked) 4.dp else 2.dp,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiText(
                text = ui.emoji,
                fontSize = 30.sp,
                modifier = Modifier.graphicsLayer { alpha = if (item.unlocked) 1f else LOCKED_EMOJI_ALPHA },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (item.unlocked) {
                    stringResource(R.string.insights_achievement_unlocked)
                } else {
                    stringResource(R.string.insights_achievement_progress, item.progress, item.target)
                },
                style = MaterialTheme.typography.labelMedium,
                color = secondaryText,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = stringResource(ui.nameRes), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(ui.descRes),
            style = MaterialTheme.typography.bodySmall,
            color = secondaryText,
        )
        if (!item.unlocked) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            ProgressPill(fraction = item.progress.toFloat() / item.target)
        }
    }
}

@Composable
private fun ProgressPill(fraction: Float) {
    val colors = FunTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(FunShapes.chip)
            .background(colors.surface)
            .border(width = 1.5.dp, color = colors.ink, shape = FunShapes.chip),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(colors.onDisabled),
            )
        }
    }
}

private val STICKER_TILTS = floatArrayOf(-2f, 1.5f, 2f, -1.5f)
private const val LOCKED_EMOJI_ALPHA = 0.4f

/** Accent fill and its readable text colour, cycling so neighbours differ. */
private fun FunColors.stickerFill(index: Int): Pair<Color, Color> = when (index % 5) {
    0 -> pink to onAccent
    1 -> lemon to onAccent
    2 -> mint to onAccent
    3 -> solo to onAccent
    else -> grape to onGrape
}

private fun Double.oneDecimal(locale: Locale): String = String.format(locale, "%.1f", this)
