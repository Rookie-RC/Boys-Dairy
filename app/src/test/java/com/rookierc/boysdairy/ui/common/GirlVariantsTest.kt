package com.rookierc.boysdairy.ui.common

import com.rookierc.boysdairy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.lang.reflect.Modifier
import javax.xml.parsers.DocumentBuilderFactory

class GirlVariantsTest {

    /** One resource type: its `R` inner class, its XML tag and the matching [GirlVariants] table. */
    private class Kind(val rType: String, val xmlTag: String, rClass: Class<*>, val table: Map<Int, Int>) {
        val idsByName: Map<String, Int> = rClass.fields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .associate { it.name to it.getInt(null) }
        val namesById: Map<Int, String> = idsByName.entries.associate { (name, id) -> id to name }
    }

    private val kinds = listOf(
        Kind("string", "string", R.string::class.java, GirlVariants.strings),
        Kind("array", "string-array", R.array::class.java, GirlVariants.arrays),
        Kind("plurals", "plurals", R.plurals::class.java, GirlVariants.plurals),
    )

    // ---- R class vs. GirlVariants -------------------------------------------------------------

    @Test
    fun everyGirlResourceHasAnOriginalAndIsRegistered() {
        var variants = 0
        for (kind in kinds) {
            for ((name, girlId) in kind.idsByName) {
                if (!name.endsWith(SUFFIX)) continue
                val originalName = name.removeSuffix(SUFFIX)
                val originalId = kind.idsByName[originalName]
                assertNotNull("R.${kind.rType}.$name has no original R.${kind.rType}.$originalName", originalId)
                assertEquals(
                    "R.${kind.rType}.$name is not registered in GirlVariants",
                    girlId,
                    kind.table[originalId],
                )
                variants++
            }
        }
        assertTrue("expected at least one _girl resource", variants > 0)
    }

    @Test
    fun everyRegisteredPairIsTheOriginalNamePlusTheGirlSuffix() {
        for (kind in kinds) {
            for ((originalId, girlId) in kind.table) {
                val originalName = kind.namesById[originalId]
                assertNotNull("GirlVariants key $originalId is not an R.${kind.rType} id", originalName)
                assertFalse(
                    "R.${kind.rType}.$originalName is itself a variant",
                    originalName!!.endsWith(SUFFIX),
                )
                assertEquals(
                    "wrong variant registered for R.${kind.rType}.$originalName",
                    originalName + SUFFIX,
                    kind.namesById[girlId],
                )
            }
        }
    }

    @Test
    fun disguiseAndLockScreenCopyHaveNoVariant() {
        for (kind in kinds) {
            for (originalId in kind.table.keys) {
                val name = kind.namesById[originalId] ?: continue
                assertFalse("app_name must stay \"Daily Calc\" (launcher disguise)", name == "app_name")
                assertFalse("R.${kind.rType}.$name: the lock screen must stay neutral", name.startsWith("lock_"))
            }
        }
    }

    // ---- strings.xml: format arguments --------------------------------------------------------

    @Test
    fun variantsUseTheSameFormatArgumentsAsTheOriginal() {
        var sawArguments = false
        for (path in STRING_FILES) {
            val resources = parseResources(resFile(path))
            for (kind in kinds) {
                for (originalId in kind.table.keys) {
                    val name = kind.namesById[originalId] ?: continue
                    val original = resources[kind.xmlTag to name]
                    val variant = resources[kind.xmlTag to name + SUFFIX]
                    assertNotNull("$path: <${kind.xmlTag} name=\"$name\"> not found", original)
                    assertNotNull("$path: <${kind.xmlTag} name=\"$name$SUFFIX\"> not found", variant)
                    assertTrue(
                        "$path: $name$SUFFIX has an empty entry",
                        variant!!.isNotEmpty() && variant.all { it.isNotBlank() },
                    )
                    val expected = formatArguments(original!!)
                    assertEquals(
                        "$path: format arguments of $name$SUFFIX differ from $name",
                        expected,
                        formatArguments(variant),
                    )
                    if (expected.isNotEmpty()) sawArguments = true
                }
            }
        }
        // Guards the parser itself: home_last_entry_girl, for one, takes a %1$s.
        assertTrue("no format argument found in any variant", sawArguments)
    }

    private fun resFile(relative: String): File =
        listOf("src/main/res/$relative", "app/src/main/res/$relative")
            .map(::File)
            .firstOrNull(File::isFile)
            ?: throw AssertionError("cannot find res/$relative from ${File("").absolutePath}")

    /** (tag, name) → text of a `string`, or the texts of all items of a `string-array` / `plurals`. */
    private fun parseResources(file: File): Map<Pair<String, String>, List<String>> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val result = mutableMapOf<Pair<String, String>, List<String>>()
        for (element in root.childNodes.elements()) {
            val texts = when (element.tagName) {
                "string" -> listOf(element.textContent)
                "string-array", "plurals" -> element.getElementsByTagName("item").elements().map { it.textContent }
                else -> continue
            }
            result[element.tagName to element.getAttribute("name")] = texts
        }
        return result
    }

    private fun NodeList.elements(): List<Element> = (0 until length).map(::item).filterIsInstance<Element>()

    /** All placeholders such as `%1$s` / `%2$d` across [texts]; escaped percent signs are ignored. */
    private fun formatArguments(texts: List<String>): Set<String> = texts.flatMapTo(sortedSetOf()) { text ->
        FORMAT_ARGUMENT.findAll(text.replace("%%", "").replace("\\%", "")).map { it.value }.toList()
    }

    private companion object {
        const val SUFFIX = "_girl"
        val STRING_FILES = listOf("values/strings.xml", "values-en/strings.xml")
        val FORMAT_ARGUMENT = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]""")
    }
}
