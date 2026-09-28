package com.cy.codex.app
import com.cy.codex.history_cell.transcriptWithSeparators
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.EnteredReviewModeItem
import com.cy.codex.protocol.protocol.item.ExitedReviewModeItem
import com.cy.codex.protocol.protocol.item.TurnSeparatorItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.Turn
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import java.time.Instant
import org.junit.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriptExportTest {

    @Test
    fun `markdown uses upstream headings and indents activity`() {
        val markdown = transcriptMarkdown(
            listOf(
                UserMessageItem("u", content = listOf(UserInput.Text("hello"))),
                AgentMessageItem("a", "hi there"),
                CommandExecutionItem(
                    "c", "ls", "/tmp",
                    status = CommandExecutionStatus.Completed,
                    aggregatedOutput = "file.txt",
                ),
                TurnSeparatorItem("sep", "Worked for 1m"),
            ),
        )!!
        assertTrue(markdown.startsWith("# Codex conversation\n"))
        assertTrue(markdown.contains("\n## User\n\nhello\n"))
        assertTrue(markdown.contains("\n## Assistant\n\nhi there\n"))
        assertTrue(markdown.contains("\n## Activity\n\n    command: ls\n"))
        assertTrue(markdown.contains("    file.txt"))
        assertTrue(!markdown.contains("Worked for"))
    }

    @Test
    fun `a transcript with nothing exportable is null`() {
        assertNull(transcriptMarkdown(listOf(TurnSeparatorItem("sep", "Worked for 1m"))))
        assertNull(transcriptMarkdown(emptyList()))
    }

    @Test
    fun `a prompt bracketed by review markers is not exported`() {
        val markdown = transcriptMarkdown(
            listOf(
                UserMessageItem("u1", content = listOf(UserInput.Text("visible prompt"))),
                EnteredReviewModeItem("enter", "current changes"),
                UserMessageItem("internal", content = listOf(UserInput.Text("internal review prompt"))),
                ExitedReviewModeItem("exit", "review complete"),
                UserMessageItem("u2", content = listOf(UserInput.Text("visible again"))),
            ),
        )!!
        assertTrue(markdown.contains("\n## User\n\nvisible prompt\n"))
        assertTrue(markdown.contains("\n## User\n\nvisible again\n"))
        assertTrue(markdown.contains(">> Code review started: current changes <<"))
        assertTrue(!markdown.contains("internal review prompt"))
    }

    @Test
    fun `a dropped nested review prompt is not exported`() {
        val reviewPrompt =
            "Review the current code changes (staged, unstaged, and untracked files)."
        val review = Turn(
            id = "review",
            items = listOf(
                EnteredReviewModeItem("enter", "current changes"),
                ExitedReviewModeItem("exit", "review complete"),
            ),
            status = TurnStatus.Completed,
            completedAt = Instant.parse("2026-09-18T14:33:00Z").toEpochMilli(),
        )
        val reviewChild = Turn(
            id = "review-child",
            items = listOf(
                UserMessageItem("p1", content = listOf(UserInput.Text(reviewPrompt))),
                UserMessageItem("p2", content = listOf(UserInput.Text(reviewPrompt))),
            ),
            status = TurnStatus.Interrupted,
        )

        val markdown = transcriptMarkdown(transcriptWithSeparators(listOf(review, reviewChild)))!!

        assertTrue(!markdown.contains(reviewPrompt))
        assertTrue(markdown.contains("<< Code review finished: review complete >>"))
    }
}
