package com.rookierc.boysdairy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme
import com.rookierc.boysdairy.ui.theme.accent
import java.time.Instant
import java.time.ZoneId

/** Top of the pleasure scale ([Entry.pleasure] is 1–10). */
private const val PLEASURE_MAX = 10

/** Same emoji as the pleasure block of the entry form, so the bar reads as "pleasure". */
private const val PLEASURE_EMOJI = "🔥"

/**
 * One diary entry as a compact sticker card. Shared by the history list and the calendar day sheet.
 *
 * Deliberately sparse, the full details live in the edit form: date and time, then type and
 * minutes ("Solo Quest · 12 min"), a solo entry's keyword (one line), a slim pleasure bar without
 * numbers or tier text (screen readers get the score) and at most one line of the note. Moods are
 * not shown here.
 *
 * [onDelete] = null hides the trash button; [showDate] = false shows only the time;
 * [onClick] makes the whole card tappable; it is announced as "edit this entry", which is what
 * callers use it for.
 */
@Composable
fun EntryCard(
    entry: Entry,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colors = FunTheme.colors
    val dateFormatter = rememberFormatter(R.string.pattern_date_weekday)
    val timeFormatter = rememberFormatter(R.string.pattern_time)
    val dateTime = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault())
    val whenText = if (showDate) {
        stringResource(R.string.entry_when, dateTime.format(dateFormatter), dateTime.format(timeFormatter))
    } else {
        dateTime.format(timeFormatter)
    }
    val deleteCd = stringResource(R.string.history_delete_cd)

    FunCard(
        onClick = onClick,
        onClickLabel = if (onClick != null) stringResource(R.string.entry_edit_action) else null,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            FunSurface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                containerColor = entry.type.accent(),
                contentColor = colors.onAccent,
                shadowOffset = 2.dp,
                contentAlignment = Alignment.Center,
            ) {
                EmojiText(text = entry.type.emoji(LocalEdition.current), fontSize = 26.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = whenText,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.entry_type_duration,
                        entry.durationMinutes,
                        stringResource(entry.type.labelRes()),
                        entry.durationMinutes,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                val keyword = entry.keyword?.trim()
                if (!keyword.isNullOrEmpty()) {
                    Text(
                        text = "${EditionEmoji.MUSE.current} ${stringResource(R.string.entry_keyword_line, keyword)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PleasureBar(
                    pleasure = entry.pleasure,
                    fillColor = entry.type.accent(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                if (entry.note.isNotBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "💬 ${entry.note}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceAlt)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            if (onDelete != null) {
                Spacer(Modifier.width(4.dp))
                ChunkyIconButton(
                    onClick = onDelete,
                    containerColor = colors.surface,
                    size = 48.dp,
                    shadowOffset = 2.dp,
                ) {
                    Text(
                        text = "🗑️",
                        fontSize = 20.sp,
                        modifier = Modifier.semantics { contentDescription = deleteCd },
                    )
                }
            }
        }
    }
}

/**
 * Slim "how good was it" bar: a small [PLEASURE_EMOJI] and a pill track filled to
 * [pleasure] / [PLEASURE_MAX] in [fillColor]. No number or tier text on screen; screen readers
 * hear the score instead. Track and fill are both ink-outlined (like the report's bars), so the
 * end of the fill stays visible even where the accent is close to the track in brightness.
 */
@Composable
private fun PleasureBar(pleasure: Int, fillColor: Color, modifier: Modifier = Modifier) {
    val colors = FunTheme.colors
    val description = stringResource(R.string.entry_pleasure_cd, pleasure)
    val fraction = pleasure.coerceIn(0, PLEASURE_MAX).toFloat() / PLEASURE_MAX
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiText(text = PLEASURE_EMOJI, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(FunShapes.chip)
                .background(colors.surfaceAlt)
                .border(width = 1.5.dp, color = colors.ink, shape = FunShapes.chip),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(FunShapes.chip)
                        .background(fillColor)
                        .border(width = 1.5.dp, color = colors.ink, shape = FunShapes.chip),
                )
            }
        }
    }
}
