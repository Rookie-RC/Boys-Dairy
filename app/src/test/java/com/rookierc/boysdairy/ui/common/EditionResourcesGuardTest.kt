package com.rookierc.boysdairy.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every user-visible string must go through the edition-aware lookups in EditionResources.kt,
 * otherwise the girl edition silently shows boy-edition copy there. This reads the main sources
 * and fails on anything that bypasses them, so new code that forgets is caught here.
 */
class EditionResourcesGuardTest {

    private val sourceRoot = File("src/main/java")

    /** The one file that may call the androidx / Resources lookups directly. */
    private fun isAllowed(file: File) = file.invariantPath().endsWith("/ui/common/EditionResources.kt")

    private fun File.invariantPath() = path.replace(File.separatorChar, '/')

    private fun sources(): List<File> {
        assertTrue("run from the :app module directory (missing ${sourceRoot.absolutePath})", sourceRoot.isDirectory)
        val files = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("no Kotlin sources found under $sourceRoot", files.size > 20)
        return files
    }

    private fun violations(files: List<File>, pattern: Regex): List<String> = files
        .filterNot(::isAllowed)
        .flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (pattern.containsMatchIn(line)) "${file.invariantPath()}:${index + 1}: ${line.trim()}" else null
            }
        }

    private fun assertNone(what: String, found: List<String>) {
        assertTrue("$what (use the edition-aware versions in ui/common/EditionResources.kt):\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun androidxStringLookupsAreOnlyUsedByEditionResources() {
        assertNone(
            "androidx string lookups that ignore the edition",
            violations(
                sources(),
                Regex("""androidx\.compose\.ui\.res\.(stringResource|stringArrayResource|pluralStringResource|\*)"""),
            ),
        )
    }

    @Test
    fun noDirectLookupsOfStringResourcesById() {
        assertNone(
            "direct Resources lookups of R ids",
            violations(sources(), Regex("""\bget(String|QuantityString|StringArray|Text|QuantityText|TextArray)\(\s*R\.""")),
        )
    }

    @Test
    fun theUiNeverReadsStringsFromResourcesDirectly() {
        // Catches lookups by a computed id too, e.g. resources.getString(type.labelRes()).
        val ui = sources().filter { "/boysdairy/ui/" in it.invariantPath() }
        assertNone(
            "Resources / Context string lookups in the UI",
            violations(ui, Regex("""\.get(String|QuantityString|StringArray|Text|QuantityText|TextArray)\(""")),
        )
    }
}
