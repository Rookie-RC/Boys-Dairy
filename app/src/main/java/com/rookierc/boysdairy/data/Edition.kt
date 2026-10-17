package com.rookierc.boysdairy.data

/**
 * Boy / girl edition of the interface: it only switches UI copy and decorative emoji (layout,
 * colours and data are the same). Asked once after the first unlock, changeable in Settings.
 *
 * [id] is what gets persisted (DataStore `settings`, key `edition`): never rename it.
 */
enum class Edition(val id: String) {
    BOY("boy"),
    GIRL("girl"),
    ;

    companion object {
        /** `null` for a missing or unknown stored value (= the user has not chosen yet). */
        fun fromId(id: String?): Edition? = entries.firstOrNull { it.id == id }
    }
}
