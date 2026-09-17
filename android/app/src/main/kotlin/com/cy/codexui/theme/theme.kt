package com.cy.codexui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun CodexTheme(
    colorMode: ColorSchemeMode = ColorSchemeMode.System,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorMode) { ThemeController(colorMode) }
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
