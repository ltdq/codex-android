package com.cy.codex

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Colour helpers shared by every surface; mirrors `codex-rs/tui/src/color.rs` and `style.rs`. */

/** Dark/light pair; most have no miuix tonal equivalent. */
private class Accent(val dark: Color, val light: Color) {
    @Composable
    operator fun invoke(): Color = if (isSystemInDarkTheme()) dark else light
}

private val Success = Accent(Color(0xFF6EDC8C), Color(0xFF1A9E4B))
private val Warning = Accent(Color(0xFFFFC24B), Color(0xFFE08600))

@Composable
fun successColor(): Color = Success()

@Composable
fun warningColor(): Color = Warning()

enum class ThreadStatusTone { Idle, Running, Waiting, Done, Failed }

@Composable
fun statusDotColor(tone: ThreadStatusTone): Color {
    val colors = MiuixTheme.colorScheme
    return when (tone) {
        ThreadStatusTone.Idle -> colors.onSurfaceVariantSummary
        ThreadStatusTone.Running -> colors.primary
        ThreadStatusTone.Waiting -> Warning()
        ThreadStatusTone.Done -> Success()
        ThreadStatusTone.Failed -> colors.error
    }
}

val com.cy.codex.protocol.protocol.v2.ThreadStatus.isWaitingOnUser: Boolean
    get() = this is com.cy.codex.protocol.protocol.v2.ThreadStatus.Active && activeFlags.any {
        it == com.cy.codex.protocol.protocol.v2.ThreadActiveFlag.WaitingOnApproval ||
            it == com.cy.codex.protocol.protocol.v2.ThreadActiveFlag.WaitingOnUserInput
    }

fun com.cy.codex.protocol.protocol.v2.ThreadStatus.tone(): ThreadStatusTone = when (this) {
    is com.cy.codex.protocol.protocol.v2.ThreadStatus.Idle -> ThreadStatusTone.Idle
    is com.cy.codex.protocol.protocol.v2.ThreadStatus.NotLoaded -> ThreadStatusTone.Idle
    is com.cy.codex.protocol.protocol.v2.ThreadStatus.Active ->
        if (isWaitingOnUser) ThreadStatusTone.Waiting else ThreadStatusTone.Running

    is com.cy.codex.protocol.protocol.v2.ThreadStatus.SystemError -> ThreadStatusTone.Failed
}

@Composable
fun statusPillSurface(tone: ThreadStatusTone): Color = statusDotColor(tone).copy(alpha = 0.14f)

@Composable
fun codeSurface(): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        colors.onSurface.copy(alpha = 0.06f)
    } else {
        colors.surfaceContainerHighest.copy(alpha = 0.72f)
    }
}

/**
 * Fill of a surface on top of a floating panel; dark mode lifts it with a content-colour wash
 * because no container is lighter than the panel's own.
 */
@Composable
fun raisedSurface(): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        colors.onSurface.copy(alpha = 0.055f)
    } else {
        colors.surfaceContainerHighest.copy(alpha = 0.7f)
    }
}

/** A sheet is a window-level panel: it cannot sample what is behind it, so it takes the solid step. */
@Composable
fun sheetColor(): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) colors.surfaceContainerHighest else colors.surfaceContainer
}

@Composable
fun panelColor(): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) colors.surfaceContainerHighest else colors.surfaceContainer
}

@Composable
fun glassTint(alpha: Float = 0.88f): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        colors.surfaceContainerHigh.copy(alpha = alpha)
    } else {
        colors.surface.copy(alpha = if (alpha > 0.8f) 0.94f else alpha)
    }
}

/** Context-window meter: amber at 0.7, error at 0.9. */
@Composable
fun usageColor(fraction: Float): Color = when {
    fraction >= 0.9f -> MiuixTheme.colorScheme.error
    fraction >= 0.7f -> Warning()
    else -> MiuixTheme.colorScheme.primary
}

fun fileName(path: String): String = path.substringAfterLast('/')

/** Parent directory, shortened from the left so the last folders stay readable. */
fun parentPath(path: String): String {
    val dir = path.substringBeforeLast('/', "")
    if (dir.isEmpty()) return ""
    val parts = dir.split('/')
    return if (parts.size <= 3) "$dir/" else "…/" + parts.takeLast(3).joinToString("/") + "/"
}
