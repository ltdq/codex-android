package com.cy.codexui.runtime

import android.content.Context
import android.system.Os
import com.cy.codexui.protocol.JsonRpcTransport
import com.cy.codexui.protocol.protocol.Json
import com.cy.codexui.protocol.protocol.JsonValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NativeRpcTransport(context: Context) : JsonRpcTransport {
    private val context = context.applicationContext
    private val lifecycle = Mutex()
    @Volatile private var handle = 0L

    override suspend fun start() = withContext(Dispatchers.IO) {
        lifecycle.withLock {
            if (handle != 0L) return@withLock
            val installation = ToolchainInstaller(context).install()
            NativeEnvironment.install(installation.environment)
            NativeBridge.load()
            val config = JsonValue.Obj(mapOf(
                "env" to JsonValue.Obj(installation.environment.mapValues { JsonValue.Str(it.value) }),
                "cwd" to JsonValue.Str(installation.workspace.absolutePath),
                "codexHome" to JsonValue.Str(installation.codexHome.absolutePath),
                "nativeLibraryDir" to JsonValue.Str(context.applicationInfo.nativeLibraryDir),
                "codexSelfExe" to JsonValue.Str("${context.applicationInfo.nativeLibraryDir}/libcodex_helper.so"),
                "toolchainRoot" to JsonValue.Str(installation.root.absolutePath),
                "shellPath" to JsonValue.Str("${installation.root.absolutePath}/bin/bash"),
            ))
            handle = NativeBridge.nativeStart(Json.write(config)).also {
                check(it != 0L) { "Codex returned an invalid native session." }
            }
        }
    }

    override suspend fun send(message: String) = withContext(Dispatchers.IO) {
        val active = handle
        check(active != 0L) { "Codex is not connected." }
        NativeBridge.nativeSend(active, message)
    }

    override suspend fun receive(): String? = withContext(Dispatchers.IO) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val active = handle
            if (active == 0L) return@withContext null
            NativeBridge.nativeReceive(active, 250)?.let { return@withContext it }
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        lifecycle.withLock {
            val active = handle
            handle = 0L
            if (active != 0L) NativeBridge.nativeStop(active)
        }
    }
}

private object NativeEnvironment {
    private var installed: Map<String, String>? = null

    @Synchronized
    fun install(environment: Map<String, String>) {
        val previous = installed
        if (previous != null) {
            check(previous == environment) { "Restart the app to change the native environment." }
            return
        }
        // Use Android's platform API before loading Rust or starting native workers.
        environment.forEach { (key, value) -> Os.setenv(key, value, true) }
        installed = environment.toMap()
    }
}
