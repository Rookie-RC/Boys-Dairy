package com.rookierc.boysdairy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.ui.theme.FunTheme
import com.rookierc.boysdairy.ui.theme.accent
import java.time.Instant
import java.time.ZoneId

/**
 * One diary entry as a sticker card. Shared by the history list and the calendar day sheet.
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
                EmojiText(text = entry.type.emoji(), fontSize = 26.sp)
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
                    text = stringResource(entry.type.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.entry_duration_line,
                        entry.durationMinutes,
                        stringResource(durationTierRes(entry.durationMinutes)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.history_mood_transition,
                        "${entry.moodBefore.emoji()} ${stringResource(entry.moodBefore.labelRes())}",
                        "${entry.moodAfter.emoji()} ${stringResource(entry.moodAfter.labelRes())}",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.entry_pleasure_line,
                        entry.pleasure,
                        stringResource(pleasureTierRes(entry.pleasure)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                if (entry.note.isNotBlank()) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "💬 ${entry.note}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
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
