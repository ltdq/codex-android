package com.cy.codex.keymap

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** The only file that knows how a platform key event is shaped; every hardware keyboard arrives
 * as a normal [KeyEvent], matched by the pure [CodexKeymap]. */

/** The chat root's focus target: with nothing focused, a Compose hierarchy receives no key events. */
val LocalChatKeyFocus = staticCompositionLocalOf<FocusRequester?> { null }

/** KeyUp ignored, KeyDown/KeyRepeat not, mirroring `KeyBinding::is_press` in key_hint.rs. */
fun KeyEvent.toKeyChord(): KeyChord? {
    if (type == KeyEventType.KeyUp) return null
    val native = nativeKeyEvent
    val ctrl = native.isCtrlPressed
    val alt = native.isAltPressed
    val shift = native.isShiftPressed
    return KeyChord(
        key = codexKey(native.keyCode, native.unicodeChar, modified = ctrl || alt),
        ctrl = ctrl,
        alt = alt,
        shift = shift,
    )
}

/** Preview pass so the composer's `BasicTextField` cannot consume a bound chord first. */
fun Modifier.codexHardwareKeys(
    context: KeyContext,
    onAction: (KeyAction) -> Boolean,
): Modifier = onPreviewKeyEvent { event ->
    val chord = event.toKeyChord() ?: return@onPreviewKeyEvent false
    val action = CodexKeymap.resolve(context, chord) ?: return@onPreviewKeyEvent false
    onAction(action)
}

/** Modified chords take the key's base character, so Ctrl+J stays `j` instead of the control char. */
internal fun codexKey(keyCode: Int, unicodeChar: Int, modified: Boolean): Int {
    namedKey(keyCode)?.let { return it }
    val typed = unicodeChar.takeIf { it != 0 && !Character.isISOControl(it) }?.toChar()
    val character = if (modified) baseCharacter(keyCode) else typed ?: baseCharacter(keyCode)
    return character?.code ?: CodexKeys.UNKNOWN
}

private fun namedKey(keyCode: Int): Int? = when (keyCode) {
    android.view.KeyEvent.KEYCODE_ENTER,
    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> CodexKeys.ENTER

    android.view.KeyEvent.KEYCODE_ESCAPE -> CodexKeys.ESCAPE
    android.view.KeyEvent.KEYCODE_TAB -> CodexKeys.TAB
    android.view.KeyEvent.KEYCODE_DPAD_UP -> CodexKeys.UP
    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> CodexKeys.DOWN
    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> CodexKeys.LEFT
    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> CodexKeys.RIGHT
    android.view.KeyEvent.KEYCODE_F1 -> CodexKeys.F1
    else -> null
}

private fun baseCharacter(keyCode: Int): Char? = when (keyCode) {
    in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z ->
        'a' + (keyCode - android.view.KeyEvent.KEYCODE_A)

    in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 ->
        '0' + (keyCode - android.view.KeyEvent.KEYCODE_0)

    android.view.KeyEvent.KEYCODE_SPACE -> ' '
    android.view.KeyEvent.KEYCODE_SLASH -> '/'
    else -> null
}
