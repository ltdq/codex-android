package com.cy.codexui.protocol.protocol.v2

/**
 * `command/exec` and `process/…` — running a process outside a turn.
 *
 * Mirrors `schema/typescript/v2/{CommandExecParams, CommandExecResponse, ProcessSpawnParams,
 * ProcessOutputDeltaNotification}.ts`.
 *
 * These are the "one-off command" family the TUI uses for `/shell`: the client owns the process and
 * reads its output from notifications, unlike a turn where the agent owns it and the client only
 * watches. [CommandExecParams] streams (`streamStdoutStderr`), so the companion notification is the
 * normal way to see output and [CommandExecResponse] is only the final summary.
 */

/** `command/exec` params. `command` is argv, never a shell string. */
data class CommandExecParams(
    val command: List<String>,
    val cwd: String? = null,
    val env: Map<String, String>? = null,
    val timeoutMs: Long? = null,
    /** Bytes of stdout kept before the server stops buffering. */
    val outputBytesCap: Int? = null,
    val disableOutputCap: Boolean = false,
    val disableTimeout: Boolean = false,
    /** Permission profile the command runs under; `null` means the session default. */
    val permissionProfile: String? = null,
    /** Attach to an existing process instead of starting a new one. */
    val processId: String? = null,
    /** Ask for a pty; required for anything interactive. */
    val tty: Boolean = false,
    val size: TerminalSize? = null,
    val streamStdin: Boolean = false,
    val streamStdoutStderr: Boolean = true,
)

/** `command/exec` response, returned once the process exits. */
data class CommandExecResponse(
    val exitCode: Int = 0,
    val stdout: String = "",
    val stderr: String = "",
)

/** `command/exec/write`: feed stdin, or close it. */
data class CommandExecWriteParams(
    val processId: String,
    val deltaBase64: String? = null,
    val closeStdin: Boolean = false,
)

/** `command/exec/resize`: pty window size. */
data class CommandExecResizeParams(
    val processId: String,
    val size: TerminalSize,
)

/** `command/exec/terminate`. */
data class CommandExecTerminateParams(val processId: String)

data class TerminalSize(val rows: Int, val cols: Int)

/** Which stream an output chunk came from. */
enum class CommandExecStream(val wire: String) {
    Stdout("stdout"),
    Stderr("stderr"),
    Stdin("stdin"),
    ;

    companion object {
        fun fromWire(value: String?): CommandExecStream =
            entries.firstOrNull { it.wire == value } ?: Stdout
    }
}

/** `command/exec/outputDelta`. */
data class CommandExecOutputDeltaNotification(
    val processId: String,
    val deltaBase64: String = "",
    val stream: CommandExecStream = CommandExecStream.Stdout,
    /** `true` once the byte cap was hit, so the UI can say the output is truncated. */
    val capReached: Boolean = false,
)

// ---------------------------------------------------------------------------------------------
// process/* — the same idea, but for a long-lived pty the client keeps talking to
// ---------------------------------------------------------------------------------------------

/** `process/spawn` params. */
data class ProcessSpawnParams(
    val command: List<String>,
    val cwd: String? = null,
    val env: Map<String, String>? = null,
    val size: TerminalSize? = null,
    val tty: Boolean = true,
)

data class ProcessSpawnResponse(val processId: String)

data class ProcessWriteStdinParams(
    val processId: String,
    val deltaBase64: String? = null,
    val closeStdin: Boolean = false,
)

data class ProcessResizePtyParams(
    val processId: String,
    val size: TerminalSize,
)

data class ProcessKillParams(val processId: String)

/** `process/outputDelta`. */
data class ProcessOutputDeltaNotification(
    val processId: String,
    val deltaBase64: String = "",
    val stream: CommandExecStream = CommandExecStream.Stdout,
)

/** `process/exited`. */
data class ProcessExitedNotification(
    val processId: String,
    val exitCode: Int = 0,
)
