package com.cy.codex.protocol.protocol.v2

/** `TurnSteerParams` in `codex/codex-rs/app-server-protocol/src/protocol/v2/turn.rs`. */
data class TurnSteerParams(
    val threadId: String,
    val input: List<UserInput>,
    val expectedTurnId: String,
    val clientUserMessageId: String? = null,
)
