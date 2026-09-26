package com.cy.codex.runtime

object NativeBridge {
    fun load() {
        try {
            System.loadLibrary("codex_android_jni")
        } catch (error: LinkageError) {
            throw IllegalStateException("Cannot load the embedded Codex library: ${error.message}", error)
        }
    }

    /** UTF-8 bytes, not jstrings: `serde_json` emits/consumes UTF-8, and `jstring` would re-encode
 * non-BMP characters as surrogate pairs. */
    external fun nativeStart(configJson: ByteArray): Long
    external fun nativeSend(handle: Long, kind: Int, json: ByteArray)
    external fun nativeReceive(handle: Long, timeoutMillis: Int): ByteArray?
    external fun nativeStop(handle: Long)
}
