package com.rookierc.boysdairy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history (exported to `app/schemas/`):
 * - 1: the `entries` table.
 * - 2: nullable `keyword` column (solo entries' "today's muse"), see [MIGRATION_1_2].
 *
 * Every version bump needs a [Migration] in [MIGRATIONS]; never fall back to a destructive
 * migration, that would wipe the diary.
 */
@Database(entities = [EntryEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        private const val DATABASE_NAME = "boysdairy.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

/**
 * Version 1 to 2: adds the optional keyword. Nullable and without a default, exactly the column
 * Room generates for `keyword: String?` (`` `keyword` TEXT `` in schemas/.../2.json), so the
 * migrated table passes Room's schema validation; existing rows get `NULL`.
 */
internal const val MIGRATION_1_2_SQL = "ALTER TABLE `entries` ADD COLUMN `keyword` TEXT"

internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(MIGRATION_1_2_SQL)
    }
}

/** All migrations, registered when the database is opened. */
internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
