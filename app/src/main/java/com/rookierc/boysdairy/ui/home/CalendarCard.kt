package com.rookierc.boysdairy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.buildMonthGrid
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.ChunkyIconButton
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.emoji
import com.rookierc.boysdairy.ui.common.rememberFormatter
import com.rookierc.boysdairy.ui.theme.FunTheme
import com.rookierc.boysdairy.ui.theme.tint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle

/** Monday-first month calendar. Days with entries show their emoji and open the day sheet on tap. */
@Composable
fun CalendarCard(
    month: YearMonth,
    markers: Map<LocalDate, EntryType>,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FunTheme.colors
    val monthFormatter = rememberFormatter(R.string.pattern_month_year)
    val locale = LocalConfiguration.current.locales.get(0)
    val weekdayNames = remember(locale) {
        (0L..6L).map { offset ->
            DayOfWeek.MONDAY.plus(offset).getDisplayName(JavaTextStyle.NARROW, locale)
        }
    }
    val weeks = remember(month) { buildMonthGrid(month).chunked(7) }

    FunCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChunkyIconButton(onClick = onPreviousMonth, size = 44.dp, shadowOffset = 2.dp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.home_prev_month),
                )
            }
            Text(
                text = month.format(monthFormatter),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
            )
            ChunkyIconButton(onClick = onNextMonth, size = 44.dp, shadowOffset = 2.dp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_next_month),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

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
                        marker = date?.let { markers[it] },
                        isToday = date == today,
                        onClick = onDayClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(emoji = EntryType.SOLO.emoji(), label = stringResource(R.string.home_legend_solo))
            Spacer(Modifier.width(16.dp))
            LegendItem(emoji = EntryType.PARTNER.emoji(), label = stringResource(R.string.home_legend_partner))
            Spacer(Modifier.width(16.dp))
            LegendItem(
                emoji = null,
                label = stringResource(R.string.home_legend_today),
                swatch = colors.lemon,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    marker: EntryType?,
    isToday: Boolean,
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
            val fill = when {
                isToday -> colors.lemon
                marker != null -> marker.tint()
                else -> Color.Transparent
            }
            val openLabel = stringResource(R.string.home_open_day)
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
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = 14.sp,
                    fontWeight = if (isToday || marker != null) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isToday) colors.onAccent else colors.textPrimary,
                )
                if (marker != null) {
                    Text(text = marker.emoji(), fontSize = 14.sp, lineHeight = 18.sp)
                } else {
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

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
