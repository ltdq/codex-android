package com.cy.codex.protocol.protocol

import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 envelopes used on the app-server wire.
 *
 * Every message is one of four shapes, tagged by which optional members are present (`method` +
 * `id` = request, `method` only = notification, `id` + `result`/`error` = response); mirrors
 * `codex-rs/app-server-protocol/src/protocol/common.rs`. Plain data classes: the transport owns
 * serialization, so the wire format lives in one place once a real socket client lands.
 */
data class JsonRpcRequest(
    val id: RequestId,
    val method: String,
    val params: JsonElement? = null,
)

data class JsonRpcResponse(
    val id: RequestId,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
) {
    val isError: Boolean get() = error != null
}

data class JsonRpcNotification(
    val method: String,
    val params: JsonElement? = null,
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
) {
    companion object {
        const val ParseError = -32700
        const val InvalidRequest = -32600
        const val MethodNotFound = -32601
        const val InvalidParams = -32602
        const val InternalError = -32603
    }
}

/**
 * Request correlation id. The server sends numbers, but the protocol allows strings too, so the
 * pending-request table is keyed by this rather than by a bare [Long].
 */
@JvmInline
value class RequestId(val value: String) {
    constructor(number: Long) : this(number.toString())

    val asLongOrNull: Long? get() = value.toLongOrNull()

    override fun toString(): String = value
}

/** What a client may receive from the server. */
sealed interface ServerMessage {
    data class Request(val request: JsonRpcRequest) : ServerMessage
    data class Notification(val notification: JsonRpcNotification) : ServerMessage
    data class Response(val response: JsonRpcResponse) : ServerMessage
}
