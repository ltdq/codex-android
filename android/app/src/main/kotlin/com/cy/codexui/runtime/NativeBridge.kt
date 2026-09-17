package com.cy.codexui.runtime

/** The library is loaded on the IO dispatcher after the toolchain is ready. */
object NativeBridge {
    fun load() {
        try {
            System.loadLibrary("codex_android_jni")
        } catch (error: LinkageError) {
            throw IllegalStateException("Cannot load the embedded Codex library: ${error.message}", error)
        }
    }

    external fun nativeStart(configJson: String): Long
    external fun nativeSend(handle: Long, json: String)
    external fun nativeReceive(handle: Long, timeoutMillis: Int): String?
    external fun nativeStop(handle: Long)
}
