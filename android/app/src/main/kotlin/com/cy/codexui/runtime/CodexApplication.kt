package com.cy.codexui.runtime

import android.app.Application
import android.content.Context
import com.cy.codexui.CodexApp
import com.cy.codexui.protocol.AppServerClient
import com.cy.codexui.protocol.JsonRpcAppServerClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CodexApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val defaultWorkspace: String get() = File(filesDir, "workspaces/default").absolutePath
    val shellPath: String get() = File(filesDir, "runtime/toolchain/bin/bash").absolutePath
    val configPath: String get() = File(filesDir, "home/.codex/config.toml").absolutePath

    val client: AppServerClient by lazy {
        JsonRpcAppServerClient(NativeRpcTransport(this), appScope, defaultWorkspace)
    }

    // Android owns this instance across Activity recreation; transcript and pending approvals
    // stay attached to the same native session while the process remains alive.
    val app: CodexApp by lazy {
        CodexApp(
            appScope,
            getSharedPreferences("codex", Context.MODE_PRIVATE),
            this,
            client,
            defaultWorkspace,
        )
    }
}
