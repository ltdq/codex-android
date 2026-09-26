package com.cy.codex.bottom_pane

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import androidx.core.content.edit

/**
 * Submitted-draft history, newest first, deduplicated and capped; mirrors the local half of
 * codex-rs/tui/src/bottom_pane/chat_composer_history.rs and persists in the same `codex_ui`
 * preferences as the appearance options.
 */
object ComposerHistory {
    private const val FileName = "codex_ui"
    private const val Key = "composer_history"
    private const val Cap = 100

    private val json = Json

    var entries by mutableStateOf<List<String>>(emptyList())
        private set

    fun load(context: Context) {
        val raw = preferences(context).getString(Key, null) ?: return
        entries = runCatching { json.decodeFromString<List<String>>(raw) }
            .getOrDefault(emptyList())
            .filter { it.isNotBlank() }
    }

    /** Record one submission; a repeat of an older entry moves it to the top. */
    fun record(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        entries = (listOf(trimmed) + entries.filterNot { it == trimmed }).take(Cap)
        preferences(context).edit { putString(Key, json.encodeToString(entries)) }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
}
