package com.rookierc.boysdairy.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * There are no instrumented tests, so this checks the migration against the schemas Room exported
 * (app/schemas): version 2 must be exactly version 1 plus what [MIGRATION_1_2] adds, otherwise
 * Room's validation would reject the upgraded database on the device.
 */
class AppDatabaseMigrationTest {

    private fun schema(version: Int): JSONObject {
        val relative = "schemas/com.rookierc.boysdairy.data.AppDatabase/$version.json"
        val file = listOf(relative, "app/$relative").map(::File).firstOrNull(File::isFile)
            ?: throw AssertionError("cannot find $relative from ${File("").absolutePath}")
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun JSONObject.entries(): JSONObject {
        val entities = getJSONArray("entities")
        return (0 until entities.length()).map(entities::getJSONObject).single { it.getString("tableName") == "entries" }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    /** Runs [MIGRATION_1_2] against a fake database that only records the SQL it is given. */
    private fun executedSql(): List<String> {
        val executed = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args?.size == 1) {
                executed += args[0] as String
                null
            } else {
                throw UnsupportedOperationException("migration called ${method.name}")
            }
        } as SupportSQLiteDatabase
        MIGRATION_1_2.migrate(db)
        return executed
    }

    @Test
    fun migrationGoesFromOneToTwoAndIsRegistered() {
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
        assertTrue(MIGRATIONS.contains(MIGRATION_1_2))
        // One migration per step up to the current version, no gaps.
        assertEquals((1 until 2).toList(), MIGRATIONS.map { it.startVersion }.sorted())
        assertTrue(MIGRATIONS.all { it.endVersion == it.startVersion + 1 })
        assertEquals(2, schema(2).getInt("version"))
    }

    @Test
    fun migrationOnlyAddsTheNullableKeywordColumn() {
        assertEquals(listOf("ALTER TABLE `entries` ADD COLUMN `keyword` TEXT"), executedSql())
        assertEquals(MIGRATION_1_2_SQL, executedSql().single())
    }

    @Test
    fun versionTwoIsVersionOnePlusTheKeywordColumn() {
        val v1 = schema(1).entries()
        val v2 = schema(2).entries()

        // The CREATE statement Room uses for fresh installs = the old one plus the added column.
        val v1Create = v1.getString("createSql")
        assertTrue(v1Create.endsWith(")"))
        assertEquals(v1Create.removeSuffix(")") + ", `keyword` TEXT)", v2.getString("createSql"))

        val v1Fields = v1.getJSONArray("fields").objects()
        val v2Fields = v2.getJSONArray("fields").objects()
        assertEquals(v1Fields.size + 1, v2Fields.size)
        v1Fields.forEachIndexed { index, field -> assertEquals(field.toString(), v2Fields[index].toString()) }

        val keyword = v2Fields.last()
        assertEquals("keyword", keyword.getString("columnName"))
        assertEquals("keyword", keyword.getString("fieldPath"))
        assertEquals("TEXT", keyword.getString("affinity"))
        assertFalse("must be nullable, like ADD COLUMN without NOT NULL", keyword.getBoolean("notNull"))
        assertFalse("ADD COLUMN sets no default, so the schema must not expect one", keyword.has("defaultValue"))

        // Nothing else about the table changed.
        assertEquals(v1.getJSONObject("primaryKey").toString(), v2.getJSONObject("primaryKey").toString())
        assertEquals(v1.getJSONArray("indices").toString(), v2.getJSONArray("indices").toString())
        assertEquals(v1.getJSONArray("foreignKeys").toString(), v2.getJSONArray("foreignKeys").toString())
    }
}
