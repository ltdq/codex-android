package com.cy.codex.chatwidget

import com.cy.codex.ComposerImageAttachment
import com.cy.codex.SessionState
import com.cy.codex.protocol.protocol.v2.ByteRange
import com.cy.codex.protocol.protocol.v2.TextElement
import com.cy.codex.protocol.protocol.v2.UserInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputRestoreTest {
    @Test
    fun `pending messages precede the current draft and keep all attachment kinds`() {
        val retained = listOf(
            UserInput.Image(fileId = "file-1", detail = "original"),
            UserInput.Image(url = "https://example.com/image.png", detail = "high"),
            UserInput.Audio("https://example.com/audio.wav"),
            UserInput.LocalAudio("/tmp/audio.wav"),
            UserInput.Skill("android", "/skills/android"),
            UserInput.Mention("notes", "/tmp/notes"),
        )
        val restored = composeRestoredInputs(
            listOf(
                listOf(UserInput.Text("pending")) + retained,
                listOf(UserInput.Text("queued")),
                listOf(UserInput.Text("current")),
            ),
        )
        assertEquals("pending\nqueued\ncurrent", restored.text)
        assertEquals(retained, restored.retainedInputs)
        assertTrue(restored.images.isEmpty())
    }

    @Test
    fun `image labels follow attachment order when labels appear out of order`() {
        val text = "[Image #2] before [Image #1]"
        val restored = composeRestoredInputs(
            listOf(
                listOf(UserInput.Image(fileId = "remote")),
                listOf(
                    UserInput.LocalImage("/tmp/first.png", "original"),
                    UserInput.LocalImage("/tmp/second.png", "high"),
                    UserInput.Text(text, listOf(element(text, "[Image #2]"), element(text, "[Image #1]"))),
                ),
            ),
        )
        assertEquals("[Image #3] before [Image #2]", restored.text)
        assertEquals(
            listOf(
                ComposerImageAttachment("/tmp/first.png", "[Image #2]", "original"),
                ComposerImageAttachment("/tmp/second.png", "[Image #3]", "high"),
            ),
            restored.images,
        )
        assertEquals(listOf("[Image #3]", "[Image #2]"), restored.textElements.map { it.placeholder })
        assertElementText(restored)
    }

    @Test
    fun `repeated message labels are renumbered without changing literal text`() {
        val firstText = "[Image #1]"
        val secondText = "literal [Image #1], actual [Image #1]"
        val restored = composeRestoredInputs(
            listOf(
                listOf(UserInput.LocalImage("/tmp/first.png"), UserInput.Text(firstText, listOf(element(firstText, firstText)))),
                listOf(
                    UserInput.LocalImage("/tmp/second.png"),
                    UserInput.Text(secondText, listOf(element(secondText, "[Image #1]", secondText.lastIndexOf("[Image #1]")))),
                ),
            ),
        )
        assertEquals("[Image #1]\nliteral [Image #1], actual [Image #2]", restored.text)
        assertEquals(listOf("[Image #1]", "[Image #2]"), restored.images.map { it.placeholder })
        assertElementText(restored)
    }

    @Test
    fun `multibyte text and non-image elements survive label growth and message merging`() {
        val text = "图 [Image #1] 备忘"
        val images = (1..9).map { UserInput.Image(fileId = "remote-$it") }
        val restored = composeRestoredInputs(
            listOf(
                images + UserInput.Text("😀"),
                listOf(
                    UserInput.LocalImage("/tmp/image.png"),
                    UserInput.Text(text, listOf(element(text, "[Image #1]"), element(text, "备忘").copy(placeholder = null))),
                ),
            ),
        )
        assertEquals("😀\n图 [Image #10] 备忘", restored.text)
        assertEquals(ByteRange(9, 20), restored.textElements[0].byteRange)
        assertEquals(TextElement(ByteRange(21, 27), null), restored.textElements[1])
        assertEquals("[Image #10]", restored.images.single().placeholder)
    }

    @Test
    fun `a surviving noncontiguous image label stays attached to its image`() {
        val text = "剩余 [Image #3]"
        val restored = composeRestoredInputs(
            listOf(listOf(UserInput.LocalImage("/tmp/image.png"), UserInput.Text(text, listOf(element(text, "[Image #3]"))))),
        )
        assertEquals("剩余 [Image #1]", restored.text)
        assertEquals("[Image #1]", restored.images.single().placeholder)
        assertElementText(restored)
    }

    @Test
    fun `local images without text receive an editable placeholder`() {
        val restored = composeRestoredInputs(
            listOf(listOf(UserInput.LocalImage("/tmp/image.png", "original"))),
        )
        assertEquals("[Image #1]", restored.text)
        assertEquals(listOf(TextElement(ByteRange(0, 10), "[Image #1]")), restored.textElements)
        assertEquals("original", restored.images.single().detail)
    }

    @Test
    fun `malformed byte ranges do not corrupt text or lose local attachments`() {
        val restored = composeRestoredInputs(
            listOf(
                listOf(
                    UserInput.LocalImage("/tmp/image.png"),
                    UserInput.Text("图", listOf(TextElement(ByteRange(1, 3), "[Image #1]"), TextElement(ByteRange(-1, 8)))),
                ),
            ),
        )
        assertEquals("图\n[Image #1]", restored.text)
        assertEquals(listOf(TextElement(ByteRange(4, 14), "[Image #1]")), restored.textElements)
    }

    @Test
    fun `empty input produces an empty composer`() {
        assertEquals(ComposerInputDraft("", emptyList(), emptyList(), emptyList()), composeRestoredInputs(emptyList()))
    }

    @Test
    fun `removing an image changes only bound placeholders and preserves other spans`() {
        val text = "literal [Image #1]; 图 [Image #1] @notes [Image #2]"
        val restored = removeComposerInputImage(
            listOf(
                UserInput.LocalImage("/tmp/first.png"),
                UserInput.LocalImage("/tmp/second.png", "original"),
                UserInput.Mention("notes", "/tmp/notes"),
                UserInput.Text(
                    text,
                    listOf(
                        element(text, "[Image #1]", text.lastIndexOf("[Image #1]")),
                        element(text, "@notes"),
                        element(text, "[Image #2]"),
                    ),
                ),
            ),
            "/tmp/first.png",
        )
        assertEquals("literal [Image #1]; 图  @notes [Image #1]", restored.text)
        assertEquals(listOf(ComposerImageAttachment("/tmp/second.png", "[Image #1]", "original")), restored.images)
        assertEquals(listOf(UserInput.Mention("notes", "/tmp/notes")), restored.retainedInputs)
        assertEquals(listOf("@notes", "[Image #1]"), restored.textElements.map { it.placeholder })
        assertElementText(restored)
    }

    @Test
    fun `inserting multibyte text rebases untouched elements`() {
        val previous = "前 [Image #1] 后"
        val image = element(previous, "[Image #1]")
        val next = "😀$previous"
        assertEquals(
            listOf(image.copy(byteRange = ByteRange(image.byteRange.start + 4, image.byteRange.end + 4))),
            rebaseComposerTextElements(previous, next, listOf(image)),
        )
        assertEquals(listOf(image), rebaseComposerTextElements(next, previous, rebaseComposerTextElements(previous, next, listOf(image))))
    }

    @Test
    fun `replacing emoji keeps element boundaries valid`() {
        val previous = "😀[Image #1]"
        val next = "😁[Image #1]"
        val image = element(previous, "[Image #1]")
        assertEquals(listOf(image), rebaseComposerTextElements(previous, next, listOf(image)))
    }

    @Test
    fun `an edit inside one element removes only that element`() {
        val previous = "[Image #1] and @notes"
        val image = element(previous, "[Image #1]")
        val mention = element(previous, "@notes")
        val next = "[Image #12] and @notes"
        assertEquals(
            listOf(mention.copy(byteRange = ByteRange(mention.byteRange.start + 1, mention.byteRange.end + 1))),
            rebaseComposerTextElements(previous, next, listOf(image, mention)),
        )
    }

    @Test
    fun `deleting a restored image span never binds its attachment to a literal label`() {
        val original = "literal [Image #1], attached [Image #1]"
        val state = SessionState()
        state.restoreComposerInputs(
            listOf(
                listOf(
                    UserInput.LocalImage("/tmp/image.png"),
                    UserInput.Text(original, listOf(element(original, "[Image #1]", original.lastIndexOf("[Image #1]")))),
                ),
            ),
        )
        state.applyDraft("literal [Image #1], attached ")

        assertTrue(state.composerImages.isEmpty())
        assertEquals(listOf(UserInput.Text("literal [Image #1], attached ")), state.pendingTurnInputs())
    }

    @Test
    fun `a restored media-only draft submits its retained inputs without text`() {
        val retained = listOf(
            UserInput.Image(fileId = "file-image", detail = "original"),
            UserInput.Image(url = "https://example.com/image.png", detail = "high"),
            UserInput.Audio("https://example.com/audio.wav"),
            UserInput.LocalAudio("/tmp/audio.wav"),
            UserInput.Skill("android", "/skills/android"),
            UserInput.Mention("notes", "/tmp/notes"),
        )
        val state = SessionState()
        state.restoreComposerInputs(listOf(retained))

        assertEquals("", state.composerDraft)
        assertTrue(state.composerImages.isEmpty())
        assertEquals(retained, state.pendingTurnInputs())
        state.removeComposerRetainedInput(1)
        assertEquals(retained.filterIndexed { index, _ -> index != 1 }, state.pendingTurnInputs())
        assertEquals("", state.composerDraft)
    }

    @Test
    fun `local image detail and mention metadata survive editing and another recovery`() {
        val original = "图 [Image #1] @notes"
        val local = UserInput.LocalImage("/tmp/image.png", "original")
        val mention = UserInput.Mention("notes", "/tmp/notes")
        val state = SessionState()
        state.restoreComposerInputs(
            listOf(listOf(local, mention, UserInput.Text(original, listOf(element(original, "[Image #1]"), element(original, "@notes"))))),
        )
        state.applyDraft("😀 ${state.composerDraft}")
        state.restoreComposerInputs(listOf(listOf(UserInput.Text("earlier"))))

        val inputs = state.pendingTurnInputs()
        assertEquals(listOf(local), inputs.filterIsInstance<UserInput.LocalImage>())
        assertEquals(listOf(mention), inputs.filterIsInstance<UserInput.Mention>())
        val text = inputs.filterIsInstance<UserInput.Text>().single()
        assertEquals("earlier\n😀 图 [Image #1] @notes", text.text)
        assertEquals(listOf(element(text.text, "[Image #1]"), element(text.text, "@notes")), text.textElements)
    }

    @Test
    fun `removing an image and adding another preserves attachment identity through recovery`() {
        val original = "literal [Image #1]; first [Image #1]; second [Image #2]"
        val state = SessionState()
        state.restoreComposerInputs(
            listOf(
                listOf(
                    UserInput.LocalImage("/tmp/first.png", "original"),
                    UserInput.LocalImage("/tmp/second.png", "high"),
                    UserInput.Text(original, listOf(element(original, "[Image #1]", original.lastIndexOf("[Image #1]")), element(original, "[Image #2]"))),
                ),
            ),
        )
        state.removeComposerImage("/tmp/first.png")
        assertEquals("literal [Image #1]; first ; second [Image #1]", state.composerDraft)
        val added = state.addComposerImage("/tmp/new.png")
        assertEquals("[Image #2]", added)
        state.applyDraft("${state.composerDraft}; new $added")

        val inputs = state.pendingTurnInputs()
        assertEquals(
            listOf(UserInput.LocalImage("/tmp/second.png", "high"), UserInput.LocalImage("/tmp/new.png")),
            inputs.filterIsInstance<UserInput.LocalImage>(),
        )
        val recovered = SessionState()
        recovered.restoreComposerInputs(listOf(inputs))
        assertEquals(inputs, recovered.pendingTurnInputs())
        val text = recovered.pendingTurnInputs().filterIsInstance<UserInput.Text>().single()
        assertEquals(text.text.lastIndexOf("[Image #1]"), text.textElements.first().byteRange.start)
    }

    @Test
    fun `a new image label does not reuse an existing literal label`() {
        val state = SessionState()
        state.applyDraft("literal [Image #1]")
        val placeholder = state.addComposerImage("/tmp/new.png")
        assertEquals("[Image #2]", placeholder)
        state.applyDraft("${state.composerDraft}; attached $placeholder")

        assertEquals(listOf(UserInput.LocalImage("/tmp/new.png")), state.pendingTurnInputs().filterIsInstance<UserInput.LocalImage>())
        val text = state.pendingTurnInputs().filterIsInstance<UserInput.Text>().single()
        assertEquals(listOf(element(text.text, "[Image #2]")), text.textElements)
    }

    private fun element(text: String, value: String, index: Int = text.indexOf(value)): TextElement {
        val start = text.substring(0, index).encodeToByteArray().size
        return TextElement(ByteRange(start, start + value.encodeToByteArray().size), value)
    }

    private fun assertElementText(restored: ComposerInputDraft) {
        val bytes = restored.text.encodeToByteArray()
        for (element in restored.textElements) {
            assertEquals(element.placeholder, bytes.copyOfRange(element.byteRange.start, element.byteRange.end).decodeToString())
        }
    }
}
