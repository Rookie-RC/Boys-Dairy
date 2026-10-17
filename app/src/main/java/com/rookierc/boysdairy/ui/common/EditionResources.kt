package com.rookierc.boysdairy.ui.common

import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.rookierc.boysdairy.data.Edition
import androidx.compose.ui.res.pluralStringResource as androidxPluralStringResource
import androidx.compose.ui.res.stringArrayResource as androidxStringArrayResource
import androidx.compose.ui.res.stringResource as androidxStringResource

/*
 * Edition-aware string lookup. Code always names the base (boy edition) resource id; with the girl
 * edition active the id is swapped for its variant from [GirlVariants] when there is one.
 *
 * This is the only file allowed to call androidx's stringResource / stringArrayResource /
 * pluralStringResource or Resources.getString & co. directly (EditionResourcesGuardTest checks).
 */

/**
 * The edition of the unlocked session's UI. Provided by `BoysDairyRoot` only around the main
 * navigation, so the lock screen (outside it) always shows the shared base copy and never hints
 * at the user's choice.
 */
val LocalEdition = staticCompositionLocalOf { Edition.BOY }

/** [id] itself, or its girl variant from [girlVariants] when [edition] is GIRL and one exists. */
internal fun resolveEditionRes(id: Int, edition: Edition, girlVariants: Map<Int, Int>): Int = when (edition) {
    Edition.BOY -> id
    Edition.GIRL -> girlVariants[id] ?: id
}

@StringRes
fun editionStringRes(@StringRes id: Int, edition: Edition): Int =
    resolveEditionRes(id, edition, GirlVariants.strings)

@ArrayRes
fun editionArrayRes(@ArrayRes id: Int, edition: Edition): Int =
    resolveEditionRes(id, edition, GirlVariants.arrays)

@PluralsRes
fun editionPluralsRes(@PluralsRes id: Int, edition: Edition): Int =
    resolveEditionRes(id, edition, GirlVariants.plurals)

// ---- Composable lookups: same names and signatures as androidx.compose.ui.res -----------------

/** Edition-aware [androidx.compose.ui.res.stringResource]. */
@Composable
@ReadOnlyComposable
fun stringResource(@StringRes id: Int): String =
    androidxStringResource(editionStringRes(id, LocalEdition.current))

/** Edition-aware [androidx.compose.ui.res.stringResource] with format arguments. */
@Composable
@ReadOnlyComposable
fun stringResource(@StringRes id: Int, vararg formatArgs: Any): String =
    androidxStringResource(editionStringRes(id, LocalEdition.current), *formatArgs)

/** Edition-aware [androidx.compose.ui.res.stringArrayResource]. */
@Composable
@ReadOnlyComposable
fun stringArrayResource(@ArrayRes id: Int): Array<String> =
    androidxStringArrayResource(editionArrayRes(id, LocalEdition.current))

/** Edition-aware [androidx.compose.ui.res.pluralStringResource]. */
@Composable
@ReadOnlyComposable
fun pluralStringResource(@PluralsRes id: Int, count: Int): String =
    androidxPluralStringResource(editionPluralsRes(id, LocalEdition.current), count)

/** Edition-aware [androidx.compose.ui.res.pluralStringResource] with format arguments. */
@Composable
@ReadOnlyComposable
fun pluralStringResource(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
    androidxPluralStringResource(editionPluralsRes(id, LocalEdition.current), count, *formatArgs)

// ---- Outside composition (callbacks): the caller passes the edition it captured -------------

fun Resources.editionString(@StringRes id: Int, edition: Edition, vararg formatArgs: Any): String {
    val resolved = editionStringRes(id, edition)
    // No arguments: skip formatting so a literal "%" in the text stays untouched.
    return if (formatArgs.isEmpty()) getString(resolved) else getString(resolved, *formatArgs)
}

fun Resources.editionQuantityString(
    @PluralsRes id: Int,
    quantity: Int,
    edition: Edition,
    vararg formatArgs: Any,
): String {
    val resolved = editionPluralsRes(id, edition)
    return if (formatArgs.isEmpty()) {
        getQuantityString(resolved, quantity)
    } else {
        getQuantityString(resolved, quantity, *formatArgs)
    }
}

fun Resources.editionStringArray(@ArrayRes id: Int, edition: Edition): Array<String> =
    getStringArray(editionArrayRes(id, edition))
