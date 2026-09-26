package com.cy.codex

import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.SandboxMode
import com.cy.codex.protocol.protocol.v2.SandboxPolicy
import com.cy.codex.protocol.protocol.v2.ThreadStartParams
import com.cy.codex.protocol.protocol.v2.UserInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement

/** Mirrors `tui/src/temporary_structured_request.rs`: ephemeral, read-only, never asks for
 * approval, only the final agent message is read back. */
internal const val StructuredTurnTimeoutMs = 30_000L

/** Agent-message budget, the `collect_structured_response` 8 KiB cap. */
internal const val MaxStructuredResponseChars = 8 * 1024

/** The thread stays unsubscribed: it is ephemeral, and no surface can open it. */
internal suspend fun structuredTurn(
    client: AppServerClient,
    cwd: String,
    model: String?,
    developerInstructions: String?,
    prompt: String,
    outputSchema: JsonElement,
    effort: ReasoningEffort? = null,
): Result<String> = runCatching {
    val session = client.startThread(
        ThreadStartParams(
            cwd = cwd,
            model = model?.takeIf { it.isNotBlank() },
            ephemeral = true,
            approvalPolicy = AskForApproval.Never,
            sandbox = SandboxPolicy(SandboxMode.ReadOnly),
            developerInstructions = developerInstructions,
        ),
    ).getOrThrow()
    val threadId = session.threadId
    try {
        client.startTurn(
            threadId = threadId,
            inputs = listOf(UserInput.Text(prompt)),
            outputSchema = outputSchema,
            effort = effort,
        ).getOrThrow()
        // Read the transcript back after completion; item bodies are authoritative there.
        withTimeoutOrNull(StructuredTurnTimeoutMs) {
            client.events.first { event ->
                event is AppServerEvent.TurnCompleted && event.threadId == threadId
            }
        } ?: error("The structured request timed out")
        val items = client.readThread(
            com.cy.codex.protocol.protocol.v2.ThreadReadParams(threadId),
        ).getOrThrow().items
        items.asReversed()
            .filterIsInstance<AgentMessageItem>()
            .firstOrNull { it.text.isNotBlank() }
            ?.text
            ?.take(MaxStructuredResponseChars)
            .orEmpty()
    } finally {
        client.unsubscribeThread(threadId)
    }
}

internal fun firstUserMessageText(items: List<com.cy.codex.protocol.protocol.item.ThreadItem>): String? =
    items.filterIsInstance<UserMessageItem>()
        .firstOrNull()
        ?.content
        ?.filterIsInstance<UserInput.Text>()
        ?.joinToString("\n") { it.text }
        ?.takeIf { it.isNotBlank() }
