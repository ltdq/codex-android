package com.cy.codex.bottom_pane.chat_composer

/**
 * What a composer line beginning with `/` is; mirrors
 * codex-rs/tui/src/bottom_pane/chat_composer/slash_input.rs::validate_submission.
 */
sealed interface SlashInput {
    data class Command(val name: String, val args: String) : SlashInput

    data class Unknown(val name: String) : SlashInput

    data object NotCommand : SlashInput
}

fun classifySlashInput(text: String, known: Set<String>): SlashInput {
    val line = text.trimStart()
    if (!line.startsWith("/")) return SlashInput.NotCommand
    val stripped = line.substring(1)
    val name = stripped.takeWhile { !it.isWhitespace() }
    if (name.isEmpty() || name.contains('/')) return SlashInput.NotCommand
    val args = stripped.substring(name.length).trim()
    return if (name in known) SlashInput.Command(name, args) else SlashInput.Unknown(name)
}
