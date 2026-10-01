package com.cy.codex

import org.junit.Test
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which scheme the shell's own colours answer to: the one the theme was built for, the device
 * filling in only for the modes that follow it.
 */
class ColorTest {

    @Test
    fun `a theme that names its own scheme answers for itself`() {
        assertTrue(schemeIsDark(ColorSchemeMode.Dark, systemDark = false))
        assertTrue(schemeIsDark(ColorSchemeMode.MonetDark, systemDark = false))
        assertFalse(schemeIsDark(ColorSchemeMode.Light, systemDark = true))
        assertFalse(schemeIsDark(ColorSchemeMode.MonetLight, systemDark = true))
    }

    @Test
    fun `a theme that follows the device takes the device's answer`() {
        assertTrue(schemeIsDark(ColorSchemeMode.System, systemDark = true))
        assertTrue(schemeIsDark(ColorSchemeMode.MonetSystem, systemDark = true))
        assertFalse(schemeIsDark(ColorSchemeMode.System, systemDark = false))
        assertFalse(schemeIsDark(ColorSchemeMode.MonetSystem, systemDark = false))
    }

    @Test
    fun `a theme with no mode yet follows the device`() {
        assertTrue(schemeIsDark(mode = null, systemDark = true))
        assertFalse(schemeIsDark(mode = null, systemDark = false))
    }
}
