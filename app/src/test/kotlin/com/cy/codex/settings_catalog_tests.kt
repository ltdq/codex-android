package com.cy.codex

import com.cy.codex.chatwidget.SettingsGroup
import com.cy.codex.chatwidget.SettingsSection
import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings sidebar is the desktop app's four groups of nineteen pages, and every row of every
 * page has copy in both locales: the pages are data, so a string that only one locale defines shows
 * up as a half-translated page on the device.
 */
class SettingsCatalogTest {

    private val moduleDir = File(".")
    private val kotlinRoot = File(moduleDir, "src/main/kotlin")
    private val locales =
        listOf(File(moduleDir, "src/main/res/values"), File(moduleDir, "src/main/res/values-zh-rCN"))

    @Test
    fun `the sidebar is four groups of nineteen pages, in the desktop app's order`() {
        assertEquals(
            listOf(
                SettingsGroup.Personal,
                SettingsGroup.Integrations,
                SettingsGroup.Coding,
                SettingsGroup.Archived,
            ),
            SettingsGroup.entries,
        )
        // 个人 8, 集成 4, 编码 6, 已归档 1 — the desktop sidebar's own counts.
        assertEquals(listOf(8, 4, 6, 1), SettingsGroup.entries.map { SettingsSection.of(it).size })
        assertEquals(19, SettingsSection.entries.size)
    }

    @Test
    fun `the pages the desktop capture shows are the pages the menu lists`() {
        assertEquals(
            listOf(
                "General",
                "Import",
                "Appearance",
                "Voice",
                "Agent",
                "Personalization",
                "Pets",
                "Shortcuts",
                "Plugins",
                "Computer",
                "Snapshots",
                "Browser",
                "Hooks",
                "Connections",
                "Review",
                "Git",
                "Environment",
                "Worktrees",
                "ArchivedChats",
            ),
            SettingsSection.entries.map { it.name },
        )
    }

    @Test
    fun `every string a settings page uses is defined in both locales`() {
        val referenced =
            settingsSources()
                .flatMap { file ->
                    Regex("R\\.string\\.([A-Za-z0-9_]+)").findAll(file.readText()).map { it.groupValues[1] }
                }
                .toSet()
        assertTrue(referenced.isNotEmpty(), "no settings source references a string")

        locales.forEach { locale ->
            val defined = localeStringNames(locale)
            val missing = referenced.filterNot { it in defined }.sorted()
            assertTrue(missing.isEmpty(), "${locale.name} does not define: $missing")
        }
    }

    @Test
    fun `the settings catalog carries the same page titles in both locales`() {
        val names =
            locales.map { locale ->
                val file = File(locale, "strings_settings_catalog.xml")
                assertTrue(file.isFile, "missing ${file.path}")
                stringNames(file)
            }
        assertEquals(names[0], names[1], "the locales do not define the same catalog strings")
        assertTrue(
            names[0].containsAll(SettingsGroup.entries.map { "settings_group_${it.name.lowercase()}" }),
            "a settings group has no title",
        )
    }

    /** Every `settings_*.kt` source, which is where the ported pages and their copy live. */
    private fun settingsSources(): List<File> =
        File(kotlinRoot, "com/cy/codex/chatwidget")
            .listFiles { file -> file.isFile && file.name.startsWith("settings") && file.extension == "kt" }
            .orEmpty()
            .toList()

    private fun stringNames(file: File): Set<String> =
        Regex("<string name=\"([A-Za-z0-9_]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toSet()

    private fun localeStringNames(directory: File): Set<String> =
        directory
            .listFiles { file -> file.isFile && file.name.startsWith("strings") && file.extension == "xml" }
            .orEmpty()
            .flatMap { stringNames(it) }
            .toSet()
}
