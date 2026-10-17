package com.rookierc.boysdairy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.DaySummary
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.buildMonthGrid
import com.rookierc.boysdairy.ui.common.ChunkyButtonSlot
import com.rookierc.boysdairy.ui.common.ChunkyIconButton
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.LocalEdition
import com.rookierc.boysdairy.ui.common.emoji
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.common.rememberFormatter
import com.rookierc.boysdairy.ui.common.stringResource
import com.rookierc.boysdairy.ui.theme.FunTheme
import com.rookierc.boysdairy.ui.theme.tint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle

/** Horizontal distance a swipe on the grid must cover before it flips the month. */
private val SwipeThreshold: Dp = 56.dp

/**
 * Monday-first month calendar. Days with entries show their emoji (plus a count badge when there
 * is more than one) and open the day sheet on tap. Months after the one containing [today] cannot
 * be reached (there is nothing in the future); away from it a "back to this month" button shows.
 * Swiping the grid sideways also flips the month (swipe left = next), with the same limit.
 */
@Composable
fun CalendarCard(
    month: YearMonth,
    days: Map<LocalDate, DaySummary>,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FunTheme.colors
    val monthFormatter = rememberFormatter(R.string.pattern_month_year)
    val dayFormatter = rememberFormatter(R.string.pattern_date_weekday)
    val locale = LocalConfiguration.current.locales.get(0)
    val weekdayNames = remember(locale) {
        (0L..6L).map { offset ->
            DayOfWeek.MONDAY.plus(offset).getDisplayName(JavaTextStyle.NARROW, locale)
        }
    }
    val weeks = remember(month) { buildMonthGrid(month).chunked(7) }
    val currentMonth = YearMonth.from(today)
    val canGoNext = month < currentMonth

    // Swipe on the grid: the gesture coroutine outlives recompositions, so it reads the latest
    // limit and callbacks through these states instead of the values it was started with.
    val swipeThresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentCanGoNext by rememberUpdatedState(canGoNext)
    val currentOnPreviousMonth by rememberUpdatedState(onPreviousMonth)
    val currentOnNextMonth by rememberUpdatedState(onNextMonth)

    FunCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChunkyIconButton(onClick = onPreviousMonth, size = 44.dp, shadowOffset = 2.dp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.home_prev_month),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = month.format(monthFormatter),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (month != currentMonth) {
                    Spacer(Modifier.height(4.dp))
                    ChunkyButtonSlot(
                        onClick = onCurrentMonth,
                        containerColor = colors.lemon,
                        shadowOffset = 2.dp,
                        minHeight = 32.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        EmojiText(text = "📍", fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.home_this_month),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            ChunkyIconButton(
                onClick = onNextMonth,
                enabled = canGoNext,
                size = 44.dp,
                shadowOffset = 2.dp,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_next_month),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .swipeToChangeMonth(
                    thresholdPx = swipeThresholdPx,
                    isRtl = isRtl,
                    onSwipeToNext = { if (currentCanGoNext) currentOnNextMonth() },
                    onSwipeToPrevious = { currentOnPreviousMonth() },
                ),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayNames.forEach { name ->
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        DayCell(
                            date = date,
                            summary = date?.let { days[it] },
                            isToday = date == today,
                            dayFormatter = dayFormatter,
                            onClick = onDayClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        val edition = LocalEdition.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(emoji = EntryType.SOLO.emoji(edition), label = stringResource(R.string.home_legend_solo))
            Spacer(Modifier.width(16.dp))
            LegendItem(emoji = EntryType.PARTNER.emoji(edition), label = stringResource(R.string.home_legend_partner))
            Spacer(Modifier.width(16.dp))
            LegendItem(
                emoji = null,
                label = stringResource(R.string.home_legend_today),
                swatch = colors.lemon,
            )
        }
    }
}

/**
 * Calls [onSwipeToNext] / [onSwipeToPrevious] when a horizontal drag of at least [thresholdPx]
 * ends: towards the start of the line (left in LTR) = next month, towards the end = previous.
 *
 * detectHorizontalDragGestures only claims a gesture once it has moved past the touch slop
 * *horizontally*, so a vertical drag still scrolls the home screen and a tap still reaches the
 * day cells (once a swipe is claimed, its consumed moves cancel their pending click). Swiping is
 * a shortcut for the arrow buttons, which stay the accessible way to change months.
 */
private fun Modifier.swipeToChangeMonth(
    thresholdPx: Float,
    isRtl: Boolean,
    onSwipeToNext: () -> Unit,
    onSwipeToPrevious: () -> Unit,
): Modifier = pointerInput(thresholdPx, isRtl) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onDragCancel = { totalDrag = 0f },
        onDragEnd = {
            // Positive = towards the end of the line (right in LTR).
            val towardsEnd = if (isRtl) -totalDrag else totalDrag
            when {
                towardsEnd <= -thresholdPx -> onSwipeToNext()
                towardsEnd >= thresholdPx -> onSwipeToPrevious()
            }
            totalDrag = 0f
        },
    ) { change, dragAmount ->
        change.consume()
        totalDrag += dragAmount
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    summary: DaySummary?,
    isToday: Boolean,
    dayFormatter: DateTimeFormatter,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            val colors = FunTheme.colors
            val shape = RoundedCornerShape(12.dp)
            val marker = summary?.type
            val count = summary?.count ?: 0
            val fill = when {
                isToday -> colors.lemon
                marker != null -> marker.tint()
                else -> Color.Transparent
            }
            val openLabel = stringResource(R.string.home_open_day)
            // One node per day for screen readers: "date, N entries" (the visible bits are hidden).
            val description = quantityString(R.plurals.home_day_cd, count, date.format(dayFormatter), count)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(fill)
                    .then(if (isToday) Modifier.border(2.dp, colors.ink, shape) else Modifier)
                    .then(
                        if (marker != null) {
                            Modifier.clickable(
                                onClickLabel = openLabel,
                                role = Role.Button,
                                onClick = { onClick(date) },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .semantics(mergeDescendants = true) { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = 14.sp,
                    fontWeight = if (isToday || marker != null) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isToday) colors.onAccent else colors.textPrimary,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                if (marker != null) {
                    Text(
                        text = marker.emoji(LocalEdition.current),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                } else {
                    Spacer(Modifier.height(18.dp))
                }
            }
            if (count > 1) {
                CountBadge(count = count, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

/** Little grape sticker with the number of entries of a day (shown from 2 on). */
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = FunTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .clip(shape)
            .background(colors.grape)
            .border(1.dp, colors.ink, shape)
            .padding(horizontal = 3.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString(),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onGrape,
        )
    }
}

private const val MAX_BADGE_COUNT = 9

@Composable
private fun LegendItem(emoji: String?, label: String, swatch: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (emoji != null) {
            EmojiText(text = emoji, fontSize = 14.sp)
        } else if (swatch != null) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(swatch)
                    .border(2.dp, FunTheme.colors.ink, RoundedCornerShape(4.dp)),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FunTheme.colors.textSecondary,
        )
    }
}
