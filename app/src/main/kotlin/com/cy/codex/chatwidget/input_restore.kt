package com.cy.codex.chatwidget

import com.cy.codex.ComposerImageAttachment
import com.cy.codex.imagePlaceholder
import com.cy.codex.protocol.protocol.v2.ByteRange
import com.cy.codex.protocol.protocol.v2.TextElement
import com.cy.codex.protocol.protocol.v2.UserInput

internal data class ComposerInputDraft(
    val text: String,
    val images: List<ComposerImageAttachment>,
    val retainedInputs: List<UserInput>,
    val textElements: List<TextElement>,
)

/** Restores pending input with attachments (`codex/codex-rs/tui/src/chatwidget/input_restore.rs`). */
internal fun composeRestoredInputs(messages: List<List<UserInput>>): ComposerInputDraft {
    val retained = messages.flatten().filter { it !is UserInput.Text && it !is UserInput.LocalImage }
    val images = mutableListOf<ComposerImageAttachment>()
    val combined = RestoredInputText()
    val remoteImageCount = retained.count { it is UserInput.Image }
    for (message in messages) {
        val localImages = message.filterIsInstance<UserInput.LocalImage>()
        val textInputs = message.filterIsInstance<UserInput.Text>()
        val originalRemoteCount = message.count { it is UserInput.Image }
        val labels = textInputs.flatMap { input ->
            validInputElements(input).mapNotNull { element ->
                val label = input.text.encodeToByteArray()
                    .copyOfRange(element.byteRange.start, element.byteRange.end).decodeToString()
                imageLabelNumber(label)?.takeIf { it > originalRemoteCount }?.let { label }
            }
        }.distinct().sortedBy { imageLabelNumber(it) }
        val mapping = localImages.mapIndexed { index, input ->
            val original = if (labels.size == localImages.size) labels[index] else imagePlaceholder(originalRemoteCount + index + 1)
            val replacement = imagePlaceholder(remoteImageCount + images.size + 1)
            images.add(ComposerImageAttachment(input.path, replacement, input.detail))
            original to replacement
        }.toMap()
        val restored = RestoredInputText()
        val matched = mutableSetOf<String>()
        for (input in textInputs) {
            restored.append(remapInputText(input, mapping, matched))
        }
        for ((original, replacement) in mapping) {
            if (original !in matched) {
                restored.append(
                    UserInput.Text(
                        replacement,
                        listOf(TextElement(ByteRange(0, replacement.length), replacement)),
                    ),
                )
            }
        }
        combined.append(restored.input())
    }
    val input = combined.input()
    return ComposerInputDraft(input.text, images, retained, input.textElements)
}

internal fun removeComposerInputImage(inputs: List<UserInput>, path: String): ComposerInputDraft {
    val draft = composeRestoredInputs(listOf(inputs))
    val index = draft.images.indexOfFirst { it.path == path }
    if (index < 0) return draft
    val removed = draft.images[index]
    val text = remapInputText(
        UserInput.Text(draft.text, draft.textElements),
        mapOf(removed.placeholder to ""),
        mutableSetOf(),
    ).let { input -> input.copy(textElements = input.textElements.filter { it.byteRange.start < it.byteRange.end }) }
    val images = draft.images.filterIndexed { imageIndex, _ -> imageIndex != index }
        .map { UserInput.LocalImage(it.path, it.detail) }
    return composeRestoredInputs(listOf(draft.retainedInputs + images + text))
}

/** Keeps untouched UTF-8 spans attached to their text while the composer changes. */
internal fun rebaseComposerTextElements(
    previous: String,
    next: String,
    elements: List<TextElement>,
): List<TextElement> {
    val valid = validInputElements(UserInput.Text(previous, elements))
    if (previous == next) return valid
    var prefix = 0
    while (prefix < previous.length && prefix < next.length) {
        val point = previous.codePointAt(prefix)
        if (point != next.codePointAt(prefix)) break
        prefix += Character.charCount(point)
    }
    var suffix = 0
    while (previous.length - suffix > prefix && next.length - suffix > prefix) {
        val point = previous.codePointBefore(previous.length - suffix)
        if (point != next.codePointBefore(next.length - suffix)) break
        suffix += Character.charCount(point)
    }
    val start = previous.substring(0, prefix).encodeToByteArray().size
    val previousEnd = previous.substring(0, previous.length - suffix).encodeToByteArray().size
    val nextEnd = next.substring(0, next.length - suffix).encodeToByteArray().size
    val delta = nextEnd - previousEnd
    return valid.mapNotNull { element ->
        when {
            element.byteRange.end <= start -> element
            element.byteRange.start >= previousEnd -> element.copy(
                byteRange = ByteRange(element.byteRange.start + delta, element.byteRange.end + delta),
            )
            else -> null
        }
    }
}

private fun imageLabelNumber(label: String): Int? =
    label.takeIf { it.startsWith("[Image #") && it.endsWith(']') }
        ?.removePrefix("[Image #")?.removeSuffix("]")?.toIntOrNull()?.takeIf { it > 0 }

private fun validInputElements(input: UserInput.Text): List<TextElement> {
    val bytes = input.text.encodeToByteArray()
    var previousEnd = 0
    return input.textElements.sortedBy { it.byteRange.start }.filter { element ->
        val (start, end) = element.byteRange
        val valid = start >= previousEnd && end > start && end <= bytes.size &&
            bytes.isUtf8Boundary(start) && bytes.isUtf8Boundary(end)
        if (valid) previousEnd = end
        valid
    }
}

private fun ByteArray.isUtf8Boundary(offset: Int): Boolean =
    offset == size || (offset in indices && (this[offset].toInt() and 0xc0) != 0x80)

private fun remapInputText(
    input: UserInput.Text,
    mapping: Map<String, String>,
    matched: MutableSet<String>,
): UserInput.Text {
    val original = input.text.encodeToByteArray()
    val text = StringBuilder()
    val elements = mutableListOf<TextElement>()
    var cursor = 0
    var outputBytes = 0
    for (element in validInputElements(input)) {
        val (start, end) = element.byteRange
        text.append(original.copyOfRange(cursor, start).decodeToString())
        outputBytes += start - cursor
        val value = original.copyOfRange(start, end).decodeToString()
        val replacement = mapping[value]?.takeIf { element.placeholder == null || element.placeholder == value }
        val updated = replacement ?: value
        text.append(updated)
        val updatedEnd = outputBytes + updated.encodeToByteArray().size
        elements.add(TextElement(ByteRange(outputBytes, updatedEnd), replacement ?: element.placeholder))
        if (replacement != null) matched.add(value)
        outputBytes = updatedEnd
        cursor = end
    }
    text.append(original.copyOfRange(cursor, original.size).decodeToString())
    return UserInput.Text(text.toString(), elements)
}

private class RestoredInputText {
    private val text = StringBuilder()
    private val elements = mutableListOf<TextElement>()
    private var byteLength = 0

    fun append(input: UserInput.Text) {
        if (input.text.isEmpty()) return
        if (text.isNotEmpty()) {
            text.append('\n')
            byteLength++
        }
        text.append(input.text)
        elements.addAll(input.textElements.map { element ->
            element.copy(byteRange = ByteRange(element.byteRange.start + byteLength, element.byteRange.end + byteLength))
        })
        byteLength += input.text.encodeToByteArray().size
    }

    fun input(): UserInput.Text = UserInput.Text(text.toString(), elements.toList())
}
