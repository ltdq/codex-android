package com.cy.codex

import com.cy.codex.protocol.protocol.v2.ByteRange
import com.cy.codex.protocol.protocol.v2.TextElement

/** A local image staged in the composer; [placeholder] marks it on the wire at submission. */
data class ComposerImageAttachment(val path: String, val placeholder: String)

/** Matches upstream `MAX_IMAGE_BYTES` (chatwidget/image_submission.rs); larger files cannot fit in
 * the 32 MiB frame. */
internal const val MaxComposerImageBytes: Long = 32L * 1024 * 1024

private val ImageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp")

/** `[Image #N]` exactly as `protocol/src/models.rs` builds it. */
internal fun imagePlaceholder(index: Int): String = "[Image #$index]"

internal fun isImagePath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in ImageExtensions

internal fun isImageAttachment(mimeType: String?, name: String): Boolean =
    mimeType?.startsWith("image/") == true || isImagePath(name)

internal data class PastedImagePath(val path: String, val start: Int, val end: Int)

/** Diffing against the old draft means a character at a time never matches; the run must be
 * absolute with an image extension. */
internal fun detectPastedImagePath(previous: String, next: String): PastedImagePath? {
    if (next.length <= previous.length) return null
    var prefix = 0
    val maxPrefix = minOf(previous.length, next.length)
    while (prefix < maxPrefix && previous[prefix] == next[prefix]) prefix++
    var suffix = 0
    val maxSuffix = minOf(previous.length - prefix, next.length - prefix)
    while (suffix < maxSuffix && previous[previous.length - 1 - suffix] == next[next.length - 1 - suffix]) suffix++
    var start = prefix
    var end = next.length - suffix
    while (start < end && next[start].isWhitespace()) start++
    while (end > start && next[end - 1].isWhitespace()) end--
    if (start >= end) return null
    val raw = next.substring(start, end)
    if (raw.contains('\n')) return null
    val path = normalizePastedPath(raw) ?: return null
    if (!path.startsWith('/')) return null
    if (!isImagePath(path)) return null
    return PastedImagePath(path, start, end)
}

/** Strip clipboard decorations (quotes, `file:`), mirroring `clipboard_paste.rs`; bare paths with
 * whitespace are rejected — no shell quoting to disambiguate. */
private fun normalizePastedPath(raw: String): String? {
    val path = when {
        raw.length >= 2 && raw.first() == raw.last() && (raw.first() == '"' || raw.first() == '\'') ->
            raw.substring(1, raw.length - 1)
        raw.any { it.isWhitespace() } -> return null
        else -> raw
    }.removePrefix("file://").ifEmpty { null } ?: return null
    return path
}

/** Byte ranges of [placeholders] in [text]; offsets are UTF-8 bytes, what `ByteRange` means on the
 * wire. Deleted placeholders are skipped. */
internal fun placeholderTextElements(text: String, placeholders: List<String>): List<TextElement> {
    val elements = mutableListOf<TextElement>()
    var cursor = 0
    for (placeholder in placeholders) {
        val index = text.indexOf(placeholder, cursor)
        if (index < 0) continue
        val start = text.substring(0, index).toByteArray(Charsets.UTF_8).size
        val end = start + placeholder.toByteArray(Charsets.UTF_8).size
        elements.add(TextElement(ByteRange(start, end), placeholder))
        cursor = index + placeholder.length
    }
    return elements
}
