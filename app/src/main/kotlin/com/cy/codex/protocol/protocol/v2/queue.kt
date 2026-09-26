package com.cy.codex.protocol.protocol.v2

/**
 * `thread/queue/…` — messages submitted while a turn is already running
 * (schema/typescript/v2/ThreadQueue*.ts). The queue lives on the server; `thread/queue/changed`
 * is a *poke* with no payload, so the client answers with `thread/queue/list` and replaces its
 * copy — modelling it as "here are the queued messages" would show a stale queue forever.
 */

/** One queued user submission. */
data class QueuedSubmission(
    val id: String,
    /** Echo of the id the client sent, so an optimistic row can be reconciled. */
    val clientUserMessageId: String = "",
    val input: List<UserInput> = emptyList(),
) {
    /** Preview text for the queue row. */
    val preview: String
        get() = input.filterIsInstance<UserInput.Text>().joinToString("\n") { it.text }.trim()
}

/** `thread/queue/add` — enqueue one message behind the running turn. */
data class ThreadQueueAddParams(
    val threadId: String,
    val input: List<UserInput>,
    val clientUserMessageId: String,
)

data class ThreadQueueAddResponse(val queuedSubmission: QueuedSubmission? = null)

/** `thread/queue/list` — paged, so a long queue does not arrive in one frame. */
data class ThreadQueueListParams(
    val threadId: String,
    val cursor: String? = null,
    val limit: Int? = null,
)

data class ThreadQueueListResponse(
    val data: List<QueuedSubmission> = emptyList(),
    val nextCursor: String? = null,
)

/** `thread/queue/update` — rewrite the input of a queued entry before it starts. */
data class ThreadQueueUpdateParams(
    val threadId: String,
    val queuedSubmissionId: String,
    val input: List<UserInput>,
)

/** `thread/queue/delete` — drop a queued entry. */
data class ThreadQueueDeleteParams(
    val threadId: String,
    val queuedSubmissionId: String,
)

/** `thread/queue/reorder` — replace the whole order in one call. */
data class ThreadQueueReorderParams(
    val threadId: String,
    val queuedSubmissionIds: List<String>,
)

/** `thread/queue/start` — run a queued entry now; `null` means "the head". */
data class ThreadQueueStartParams(
    val threadId: String,
    val queuedSubmissionId: String? = null,
)
