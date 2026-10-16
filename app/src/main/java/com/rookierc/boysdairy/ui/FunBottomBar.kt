package com.rookierc.boysdairy.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.FunSurface
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme

@Immutable
data class BottomTab(
    val route: String,
    @StringRes val labelRes: Int,
    val emoji: String,
)

/** Part of the bar that is covered by the raised center button but is not bar background. */
private val BumpOverhang: Dp = 26.dp
private val BarHeight: Dp = 62.dp

/**
 * Bottom bar with four emoji tabs and a raised, chunky "check in" button in the middle.
 *
 * The raised part is *inside* this composable's own height, so the Scaffold's content padding
 * already includes it and screen content can never be covered by the button.
 *
 * @param tabs exactly the tabs left and right of the center button (first half / second half)
 */
@Composable
fun FunBottomBar(
    tabs: List<BottomTab>,
    selectedRoute: String?,
    onTabClick: (BottomTab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FunTheme.colors
    val half = tabs.size / 2

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(BumpOverhang))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.ink),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .navigationBarsPadding()
                    .height(BarHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.take(half).forEach { tab ->
                    BarItem(
                        tab = tab,
                        selected = tab.route == selectedRoute,
                        onClick = { onTabClick(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Center slot: the button floats above, only its label lives in the bar. It uses the
                // same column layout as BarItem (with an invisible stand-in for the emoji pill), so
                // its baseline matches the other labels and keeps a gap below the raised button.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmojiText(
                        text = "➕",
                        fontSize = 20.sp,
                        modifier = Modifier
                            .alpha(0f)
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                    )
                    Text(
                        text = stringResource(R.string.tab_add),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                }

                tabs.drop(half).forEach { tab ->
                    BarItem(
                        tab = tab,
                        selected = tab.route == selectedRoute,
                        onClick = { onTabClick(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        AddBump(
            onClick = onAddClick,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun AddBump(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FunTheme.colors
    FunSurface(
        modifier = modifier.size(64.dp),
        shape = CircleShape,
        containerColor = colors.pink,
        contentColor = colors.onAccent,
        shadowOffset = 4.dp,
        borderWidth = 3.dp,
        onClick = onClick,
        role = Role.Button,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.add_entry_cd),
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun BarItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FunTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val pillShape = FunShapes.chip
        Box(
            modifier = Modifier
                .clip(pillShape)
                .background(if (selected) colors.lemon else Color.Transparent)
                .then(
                    if (selected) Modifier.border(2.dp, colors.ink, pillShape) else Modifier,
                )
                .padding(horizontal = 14.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmojiText(text = tab.emoji, fontSize = 20.sp)
        }
        Text(
            text = stringResource(tab.labelRes),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
