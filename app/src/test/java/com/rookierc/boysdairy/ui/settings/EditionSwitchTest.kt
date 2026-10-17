package com.rookierc.boysdairy.ui.settings

import com.rookierc.boysdairy.data.Edition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Switching the edition in Settings: nothing is stored before the dialog is confirmed. */
class EditionSwitchTest {

    private val applied = mutableListOf<Edition>()
    private val switch = EditionSwitch { applied += it }

    @Test
    fun pickingTheOtherEditionOnlyAsks() {
        switch.request(target = Edition.GIRL, current = Edition.BOY)

        assertEquals(Edition.GIRL, switch.pending.value)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun confirmingStoresItOnce() {
        switch.request(target = Edition.GIRL, current = Edition.BOY)

        switch.confirm()
        assertEquals(listOf(Edition.GIRL), applied)
        assertNull(switch.pending.value)

        switch.confirm() // e.g. a double tap on the button
        assertEquals(listOf(Edition.GIRL), applied)
    }

    @Test
    fun cancellingStoresNothing() {
        switch.request(target = Edition.BOY, current = Edition.GIRL)

        switch.cancel()
        assertNull(switch.pending.value)
        assertTrue(applied.isEmpty())

        switch.confirm()
        assertTrue(applied.isEmpty())
    }

    @Test
    fun pickingTheEditionInUseDoesNothing() {
        switch.request(target = Edition.BOY, current = Edition.BOY)
        assertNull(switch.pending.value)

        switch.confirm()
        assertTrue(applied.isEmpty())
    }

    @Test
    fun theLatestPickWins() {
        switch.request(target = Edition.GIRL, current = Edition.BOY)
        switch.request(target = Edition.BOY, current = Edition.BOY)
        assertNull(switch.pending.value)

        switch.request(target = Edition.GIRL, current = Edition.BOY)
        switch.confirm()
        assertEquals(listOf(Edition.GIRL), applied)
    }
}
