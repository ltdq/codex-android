package com.cy.codex.bottom_pane.async_questions

import com.cy.codex.protocol.protocol.v2.AsyncUserInputQuestion
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mirrors codex-rs/context-fragments/src/answered_question.rs and tui/src/bottom_pane/async_questions/state.rs::append. */
class AsyncQuestionsTest {

    @Test
    fun `answer quotes the question and trims the answer`() {
        assertEquals(
            "> Which way?\n\nleft",
            AsyncQuestions.answeredText("Which way?", "  left  "),
        )
    }

    @Test
    fun `question is flattened onto one quoted line`() {
        assertEquals(
            "> First line Second line\n\nanswer",
            AsyncQuestions.answeredText("First line\nSecond line", "answer"),
        )
    }

    @Test
    fun `question is cut at 512 utf8 bytes on a character boundary`() {
        // é is two bytes; 512 bytes is exactly 256 of them.
        val question = "é".repeat(300)
        val rendered = AsyncQuestions.answeredText(question, "x")
        assertEquals("> ${"é".repeat(256)}\n\nx", rendered)
    }

    @Test
    fun `four-byte characters are not split`() {
        // 😀 is four bytes; 128 fit in 512.
        val question = "😀".repeat(200)
        val rendered = AsyncQuestions.answeredText(question, "x")
        assertEquals("> ${"😀".repeat(128)}\n\nx", rendered)
    }

    @Test
    fun `options keep the first 32 and drop over-long labels`() {
        val options = (1..40).map { "option $it" }.toMutableList()
        options[0] = "x".repeat(513)
        options[1] = "y".repeat(512)
        val normalized = AsyncQuestions.normalize(
            listOf(AsyncUserInputQuestion("Pick", options)),
        ).single()
        val kept = normalized.options!!
        assertEquals(31, kept.size)
        assertEquals("y".repeat(512), kept.first())
        assertEquals("option 3", kept[1])
        assertEquals("option 32", kept.last())
    }

    @Test
    fun `omitted and empty options stay free text`() {
        val normalized = AsyncQuestions.normalize(
            listOf(
                AsyncUserInputQuestion("No options", null),
                AsyncUserInputQuestion("Empty", emptyList()),
            ),
        )
        assertNull(normalized[0].options)
        assertEquals(emptyList(), normalized[1].options)
    }

    @Test
    fun `questions without options survive normalization`() {
        val questions = listOf(AsyncUserInputQuestion("Free text", null))
        assertEquals(questions, AsyncQuestions.normalize(questions))
    }
}
