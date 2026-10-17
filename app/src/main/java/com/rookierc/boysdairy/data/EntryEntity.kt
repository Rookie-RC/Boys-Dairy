package com.rookierc.boysdairy.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood

/**
 * Room row for a diary entry. Enums are stored by name as plain strings so the schema does not
 * depend on enum ordinals; mapping to/from the domain model happens here.
 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val durationMinutes: Int,
    val moodBefore: String,
    val moodAfter: String,
    val pleasure: Int,
    val note: String,
    /** Added in schema version 2 (nullable, no default; see [MIGRATION_1_2]). */
    val keyword: String? = null,
)

internal fun EntryEntity.toDomain(): Entry = Entry(
    id = id,
    timestamp = timestamp,
    type = enumOrDefault(type, EntryType.SOLO),
    durationMinutes = durationMinutes,
    moodBefore = enumOrDefault(moodBefore, Mood.NEUTRAL),
    moodAfter = enumOrDefault(moodAfter, Mood.NEUTRAL),
    pleasure = pleasure,
    note = note,
    keyword = keyword,
)

internal fun Entry.toEntity(): EntryEntity = EntryEntity(
    id = id,
    timestamp = timestamp,
    type = type.name,
    durationMinutes = durationMinutes,
    moodBefore = moodBefore.name,
    moodAfter = moodAfter.name,
    pleasure = pleasure,
    note = note,
    keyword = keyword,
)

private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
