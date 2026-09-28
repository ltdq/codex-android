package com.cy.codex.chatwidget

import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.UserInput

/** Uncommitted input, mirroring `codex/codex-rs/tui/src/chatwidget/user_messages.rs`. */
data class PendingSteer(val id: String, val threadId: String, val inputs: List<UserInput>, val turnId: String? = null) {
    val preview: String get() = inputs.filterIsInstance<UserInput.Text>().joinToString("\n") { it.text }

    fun matches(item: UserMessageItem): Boolean = item.clientId == id
}
