package com.cy.codex.theme

import android.content.Context
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cy.codex.Motion
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import androidx.core.content.edit

/** Client-side appearance preferences; global mutable state because the theme wraps the whole
 * composition, above `CodexApp`. */
object Appearance {
    private const val FileName = "codex_ui"
    private const val KeyThemeMode = "theme_mode"
    private const val KeyReduceMotion = "reduce_motion"
    private const val KeyShowTooltips = "show_tooltips"

    var themeMode by mutableStateOf(ColorSchemeMode.System)
        private set
    var reduceMotion by mutableStateOf(false)
        private set

    /** `tui.show_tooltips` upstream: whether a startup tip is shown on a fresh conversation. */
    var showTooltips by mutableStateOf(true)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
        themeMode = when (prefs.getString(KeyThemeMode, null)) {
            "light" -> ColorSchemeMode.Light
            "dark" -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
        reduceMotion = prefs.getBoolean(KeyReduceMotion, false)
        showTooltips = prefs.getBoolean(KeyShowTooltips, true)
        syncSystemAnimators()
    }

    /** The "Remove animations" setting zeroes `ANIMATOR_DURATION_SCALE`; re-sync on resume. */
    fun syncSystemAnimators() {
        Motion.reduced = reduceMotion || !android.animation.ValueAnimator.areAnimatorsEnabled()
    }

    fun setThemeMode(context: Context, mode: ColorSchemeMode) {
        themeMode = mode
        preferences(context).edit { putString(KeyThemeMode, mode.wire) }
    }

    fun setReduceMotion(context: Context, enabled: Boolean) {
        reduceMotion = enabled
        syncSystemAnimators()
        preferences(context).edit { putBoolean(KeyReduceMotion, enabled) }
    }

    fun setShowTooltips(context: Context, enabled: Boolean) {
        showTooltips = enabled
        preferences(context).edit { putBoolean(KeyShowTooltips, enabled) }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(FileName, Context.MODE_PRIVATE)

    private val ColorSchemeMode.wire: String
        get() = when (this) {
            ColorSchemeMode.Light -> "light"
            ColorSchemeMode.Dark -> "dark"
            else -> "system"
        }
}

@Composable
fun CodexTheme(
    colorMode: ColorSchemeMode = Appearance.themeMode,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorMode) { ThemeController(colorMode) }
    // The theme is the flag's one subscriber: a flip recomposes the tree, so `Motion` specs snap.
    Appearance.reduceMotion
    MiuixTheme(controller = controller) {
        val colors = MiuixTheme.colorScheme
        val indication = remember(colors.onBackground) {
            RoundedIndication(color = colors.onBackground)
        }
        CompositionLocalProvider(
            LocalIndication provides indication,
            content = content,
        )
    }
}
