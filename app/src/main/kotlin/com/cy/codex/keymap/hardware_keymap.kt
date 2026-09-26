package com.cy.codex.keymap

/**
 * The keyboard layer, pure Kotlin (no Compose/`android.view`) so every rule is JVM-testable;
 * `key_event_adapter.kt` is the only file that knows platform events. Bindings mirror
 * `codex-rs/tui/src/keymap.rs` (`built_in_defaults`), matching rules mirror `codex-rs/tui/src/key_hint.rs`.
 */

/** One key press; [key] is a code point or a negative sentinel from [CodexKeys]. */
data class KeyChord(
    val key: Int,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
)

/** Named keys; sentinels are negative so they cannot collide with a code point. */
object CodexKeys {
    const val UNKNOWN = 0
    const val ENTER = -1
    const val ESCAPE = -2
    const val TAB = -3
    const val UP = -4
    const val DOWN = -5
    const val LEFT = -6
    const val RIGHT = -7
    const val F1 = -8

    fun char(c: Char): Int = c.code
}

/** Where a chord is interpreted; a context's own bindings win over [Global]. */
enum class KeyContext { Global, Chat, Composer, Popup }

/** Wire-agnostic chord meaning; host hooks ([CopyLastResponse], [OpenExternalEditor]) are inert
 * without a callback. */
enum class KeyAction {
    Submit,
    InsertNewline,
    InterruptTurn,
    OpenTranscript,
    ShowShortcuts,
    PopupNext,
    PopupPrev,
    PopupAccept,
    PopupDismiss,
    ClearFocus,
    HistoryOlder,
    HistoryNewer,
    CopyLastResponse,
    OpenExternalEditor,
}

object CodexKeymap {

    /** Context first, then [KeyContext.Global]; null leaves the event to the host. */
    fun resolve(context: KeyContext, chord: KeyChord): KeyAction? {
        val normalized = normalize(chord)
        // AltGr arrives as Ctrl+Alt from a physical keyboard; it types a character, never a
        // shortcut, so a modified character key is dropped. Mirrors `is_altgr` in key_hint.rs.
        if (normalized.ctrl && normalized.alt && normalized.key > 0) return null
        return bindings(context)[normalized] ?: bindings(KeyContext.Global)[normalized]
    }

    /** `normalize_key_parts`: uppercase implies Shift and folds to lowercase, so shift+j matches
     * either spelling while ctrl+j does not match ctrl+J. */
    internal fun normalize(chord: KeyChord): KeyChord {
        if (chord.key <= 0 || chord.key > Char.MAX_VALUE.code) return chord
        val ch = chord.key.toChar()
        if (ch in 'A'..'Z') return chord.copy(key = ch.lowercaseChar().code, shift = true)
        return chord
    }

    private fun bindings(context: KeyContext): Map<KeyChord, KeyAction> = when (context) {
        KeyContext.Global -> global
        KeyContext.Chat -> chat
        KeyContext.Composer -> composer
        KeyContext.Popup -> popup
    }

    private val global: Map<KeyChord, KeyAction> = mapOf(
        // `chat.interrupt_turn`; left to the composer when no turn is running.
        KeyChord(CodexKeys.ESCAPE) to KeyAction.InterruptTurn,
        // Android never quits from a key, and the clipboard owns Ctrl+C while idle.
        KeyChord(CodexKeys.char('c'), ctrl = true) to KeyAction.InterruptTurn,
        // `app.open_transcript`: the transcript overlay, opened here as ThreadHistory.
        KeyChord(CodexKeys.char('t'), ctrl = true) to KeyAction.OpenTranscript,
        KeyChord(CodexKeys.char('/'), ctrl = true) to KeyAction.ShowShortcuts,
        KeyChord(CodexKeys.F1) to KeyAction.ShowShortcuts,
    )

    private val chat: Map<KeyChord, KeyAction> = mapOf(
        KeyChord(CodexKeys.ESCAPE) to KeyAction.InterruptTurn,
    )

    /** The host acts on these only while the field is focused. */
    private val composer: Map<KeyChord, KeyAction> = mapOf(
        KeyChord(CodexKeys.ENTER) to KeyAction.Submit,
        // `editor.insert_newline`: shift-enter, alt-enter, ctrl-j and ctrl-m.
        KeyChord(CodexKeys.ENTER, shift = true) to KeyAction.InsertNewline,
        KeyChord(CodexKeys.ENTER, alt = true) to KeyAction.InsertNewline,
        KeyChord(CodexKeys.char('j'), ctrl = true) to KeyAction.InsertNewline,
        KeyChord(CodexKeys.char('m'), ctrl = true) to KeyAction.InsertNewline,
        // `?` may arrive with or without SHIFT; gated on an empty field so it never eats typed text.
        KeyChord(CodexKeys.char('?')) to KeyAction.ShowShortcuts,
        KeyChord(CodexKeys.char('?'), shift = true) to KeyAction.ShowShortcuts,
        KeyChord(CodexKeys.ESCAPE) to KeyAction.ClearFocus,
        // `history_search_previous` / `history_search_next`.
        KeyChord(CodexKeys.char('r'), ctrl = true) to KeyAction.HistoryOlder,
        KeyChord(CodexKeys.char('s'), ctrl = true) to KeyAction.HistoryNewer,
        KeyChord(CodexKeys.char('o'), ctrl = true) to KeyAction.CopyLastResponse,
        KeyChord(CodexKeys.char('g'), ctrl = true) to KeyAction.OpenExternalEditor,
    )

    /** Enter/Tab accept, Esc dismisses (`list.accept` in keymap.rs). */
    private val popup: Map<KeyChord, KeyAction> = mapOf(
        KeyChord(CodexKeys.UP) to KeyAction.PopupPrev,
        KeyChord(CodexKeys.DOWN) to KeyAction.PopupNext,
        KeyChord(CodexKeys.ENTER) to KeyAction.PopupAccept,
        KeyChord(CodexKeys.TAB) to KeyAction.PopupAccept,
        KeyChord(CodexKeys.ESCAPE) to KeyAction.PopupDismiss,
    )
}
