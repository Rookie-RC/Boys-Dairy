package com.rookierc.boysdairy.ui.settings

import com.rookierc.boysdairy.data.Edition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The boy / girl edition is meant to be picked once, so switching it in Settings takes two steps:
 * picking the other edition only asks ([pending] is set and the confirmation dialog shows);
 * nothing is stored until [confirm]. Cancelling (or tapping outside the dialog) stores nothing.
 *
 * Plain state holder (no coroutines of its own) so the rule is unit-tested; [apply] does the write.
 */
class EditionSwitch(private val apply: (Edition) -> Unit) {

    private val _pending = MutableStateFlow<Edition?>(null)

    /** The edition waiting for confirmation; `null` = no dialog. */
    val pending: StateFlow<Edition?> = _pending.asStateFlow()

    /** Asks to switch from [current] to [target]; picking the edition already in use does nothing. */
    fun request(target: Edition, current: Edition) {
        _pending.value = if (target == current) null else target
    }

    /** Stores the pending edition (once). */
    fun confirm() {
        val target = _pending.value ?: return
        _pending.value = null
        apply(target)
    }

    fun cancel() {
        _pending.value = null
    }
}
