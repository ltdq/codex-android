package com.cy.codex.bottom_pane.async_questions

import com.cy.codex.protocol.protocol.v2.AsyncUserInputQuestion

/**
 * Framing for the questions an agent message can carry, mirroring
 * codex-rs/context-fragments/src/answered_question.rs and codex-rs/tui/src/bottom_pane/async_questions/state.rs::append;
 * the answer is an ordinary user message, so the quoted question is client-authored framing.
 */
internal object AsyncQuestions {

    /** UTF-8 byte bound on the quoted question (`AnsweredQuestion::new`). */
    const val QuestionByteLimit = 512

    /** Model-authored options beyond these bounds are dropped (`state.rs::append`). */
    const val MaxOptions = 32
    const val OptionByteLimit = 512

    /** `> {question}\n\n{answer}` with the question flattened to one bounded line; callers must not submit a blank answer. */
    fun answeredText(question: String, answer: String): String =
        "> ${flattened(question)}\n\n${answer.trim()}"

    /** Bound each question's options; take-then-filter matches the TUI, and an empty result is a free-text question. */
    fun normalize(questions: List<AsyncUserInputQuestion>): List<AsyncUserInputQuestion> =
        questions.map { question ->
            question.copy(
                options = question.options
                    ?.asSequence()
                    ?.take(MaxOptions)
                    ?.filter { it.toByteArray(Charsets.UTF_8).size <= OptionByteLimit }
                    ?.toList(),
            )
        }

    private fun flattened(question: String): String =
        question.takeUtf8Bytes(QuestionByteLimit).replace('\n', ' ').replace('\r', ' ')

    /** [limit] UTF-8 bytes from the front, stopping on a character boundary. */
    private fun String.takeUtf8Bytes(limit: Int): String {
        var bytes = 0
        var end = 0
        while (end < length) {
            val codePoint = codePointAt(end)
            val width = when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            if (bytes + width > limit) break
            bytes += width
            end += Character.charCount(codePoint)
        }
        return substring(0, end)
    }
}
