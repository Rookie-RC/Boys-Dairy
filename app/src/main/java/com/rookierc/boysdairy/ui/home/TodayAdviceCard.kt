package com.rookierc.boysdairy.ui.home

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.AdviceLevel
import com.rookierc.boysdairy.domain.AdviceReason
import com.rookierc.boysdairy.domain.TodayAdvice
import com.rookierc.boysdairy.domain.pickDistinctByDay
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.FunSurface
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.common.stringArrayResource
import com.rookierc.boysdairy.ui.common.stringResource
import com.rookierc.boysdairy.ui.theme.FunColors
import com.rookierc.boysdairy.ui.theme.FunTheme

/** 宜 / 忌 items shown per line. */
private const val ITEMS_PER_LINE = 2

/** Stamp diameter in sp, so it grows with the user's font size along with the text in it. */
private val SEAL_DIAMETER = 64.sp
private val SEAL_SHADOW = 3.dp

/**
 * "📜 Today's almanac" card of the home screen: a stamp with the verdict, the verdict itself with a
 * quip of the day, two 宜 and two 忌 everyday items and then, set apart in a quieter, more formal
 * style, one calm sentence that closes the card (no footer line under it, to keep the card light).
 * [advice] comes from [HomeViewModel]; its [TodayAdvice.day] picks everything "of the day", so the
 * whole card stays put all day and changes at midnight.
 */
@Composable
internal fun TodayAdviceCard(advice: TodayAdvice, modifier: Modifier = Modifier) {
    val colors = FunTheme.colors
    val epochDay = advice.day.toEpochDay()
    val quips = stringArrayResource(advice.reason.quipsRes())
    val calmLines = stringArrayResource(R.array.almanac_calm_lines)

    FunCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(emoji = "📜", title = stringResource(R.string.almanac_title))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AdviceSeal(level = advice.level)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = verdict(advice), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = quips[pickByDay(epochDay, quips.size)],
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        AlmanacLine(lineRes = R.string.almanac_good_line, itemsRes = R.array.almanac_good_items, epochDay = epochDay)
        Spacer(Modifier.height(4.dp))
        AlmanacLine(lineRes = R.string.almanac_bad_line, itemsRes = R.array.almanac_bad_items, epochDay = epochDay)
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = colors.ink.copy(alpha = 0.15f), thickness = 1.dp)
        Spacer(Modifier.height(14.dp))
        CalmLine(text = calmLines[pickByDay(epochDay, calmLines.size)])
    }
}

@Composable
private fun verdict(advice: TodayAdvice): String = when (advice.reason) {
    AdviceReason.LOGGED_TODAY -> stringResource(R.string.almanac_verdict_avoid_today)
    AdviceReason.STREAK -> quantityString(R.plurals.almanac_verdict_avoid_streak, advice.streakDays, advice.streakDays)
    AdviceReason.WITHIN_RHYTHM, AdviceReason.LOGGED_YESTERDAY -> stringResource(R.string.almanac_verdict_unfavorable)
    AdviceReason.NO_RHYTHM, AdviceReason.NO_ENTRIES -> stringResource(R.string.almanac_verdict_neutral)
    AdviceReason.RHYTHM_REACHED -> stringResource(R.string.almanac_verdict_favorable)
}

@ArrayRes
private fun AdviceReason.quipsRes(): Int = when (this) {
    AdviceReason.LOGGED_TODAY -> R.array.almanac_quips_avoid_today
    AdviceReason.STREAK -> R.array.almanac_quips_avoid_streak
    AdviceReason.WITHIN_RHYTHM, AdviceReason.LOGGED_YESTERDAY -> R.array.almanac_quips_unfavorable
    AdviceReason.NO_RHYTHM, AdviceReason.NO_ENTRIES -> R.array.almanac_quips_neutral
    AdviceReason.RHYTHM_REACHED -> R.array.almanac_quips_favorable
}

/** Round, slightly tilted stamp with a double ring, like a seal pressed onto an almanac page. */
@Composable
private fun AdviceSeal(level: AdviceLevel) {
    val colors = FunTheme.colors
    val text = stringResource(level.sealRes())
    val description = stringResource(level.sealDescriptionRes())
    val diameter = with(LocalDensity.current) { SEAL_DIAMETER.toDp() }
    val fontSize = sealFontSize(text)

    FunSurface(
        // FunSurface keeps the shadow inside its own bounds, so add it to the circle's size.
        modifier = Modifier
            .size(diameter + SEAL_SHADOW)
            .rotate(-8f)
            .clearAndSetSemantics { contentDescription = description },
        shape = CircleShape,
        containerColor = colors.sealFill(level),
        contentColor = colors.onAccent,
        shadowOffset = SEAL_SHADOW,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .border(width = 1.5.dp, color = colors.onAccent, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                lineHeight = fontSize * 1.15f,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** One character fills the stamp; two (不宜, NO, OK) or a short word (HOLD) get smaller. */
private fun sealFontSize(text: String): TextUnit = when (text.length) {
    1 -> 30.sp
    2 -> 20.sp
    else -> 15.sp
}

/** Text on all four fills is [FunColors.onAccent] (>= 6:1 in both themes). */
private fun FunColors.sealFill(level: AdviceLevel): Color = when (level) {
    AdviceLevel.AVOID -> danger
    AdviceLevel.UNFAVORABLE -> lemon
    AdviceLevel.NEUTRAL -> mint
    AdviceLevel.FAVORABLE -> solo
}

@StringRes
private fun AdviceLevel.sealRes(): Int = when (this) {
    AdviceLevel.AVOID -> R.string.almanac_seal_avoid
    AdviceLevel.UNFAVORABLE -> R.string.almanac_seal_unfavorable
    AdviceLevel.NEUTRAL -> R.string.almanac_seal_neutral
    AdviceLevel.FAVORABLE -> R.string.almanac_seal_favorable
}

@StringRes
private fun AdviceLevel.sealDescriptionRes(): Int = when (this) {
    AdviceLevel.AVOID -> R.string.almanac_seal_description_avoid
    AdviceLevel.UNFAVORABLE -> R.string.almanac_seal_description_unfavorable
    AdviceLevel.NEUTRAL -> R.string.almanac_seal_description_neutral
    AdviceLevel.FAVORABLE -> R.string.almanac_seal_description_favorable
}

/** "宜：早睡 · 喝杯温水": [ITEMS_PER_LINE] distinct items of the day; screen readers get a spoken separator. */
@Composable
private fun AlmanacLine(@StringRes lineRes: Int, @ArrayRes itemsRes: Int, epochDay: Long) {
    val items = stringArrayResource(itemsRes)
    val picked = pickDistinctByDay(epochDay, items.size, ITEMS_PER_LINE).map { items[it] }
    val shown = stringResource(lineRes, picked.joinToString(stringResource(R.string.almanac_item_separator)))
    val spoken = stringResource(lineRes, picked.joinToString(stringResource(R.string.almanac_item_separator_spoken)))
    Text(
        text = shown,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}

/** The sincere line: quiet serif type in the secondary colour behind a thin rule, so the change of tone shows. */
@Composable
private fun CalmLine(text: String) {
    val colors = FunTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(color = colors.textSecondary, shape = CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                lineHeight = 26.sp,
            ),
            color = colors.textSecondary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
        )
    }
}
