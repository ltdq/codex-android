package com.cy.codex.protocol

import org.junit.Test
import kotlin.test.assertTrue

class AppServerClientBindingTest {

    /** Bridges are compiler-generated interface-default delegation; real overrides are plain public methods. */
    private fun implementedMethods(): Set<String> =
        JsonRpcAppServerClient::class.java.declaredMethods
            .filterNot { it.isSynthetic || it.isBridge }
            .map { it.name.substringBefore('-') }
            .toSet()

    private fun declaredClientMethods(): Set<String> =
        AppServerClient::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name.substringBefore('-') }
            .toSet()

    @Test
    fun `every AppServerClient method is implemented by the JSON-RPC client`() {
        val implemented = implementedMethods()
        val missing = declaredClientMethods().filterNot { it in implemented }.sorted()

        assertTrue(
            missing.isEmpty(),
            "These AppServerClient methods fall through to the unsupported() default, so calling " +
                "them only yields Result.failure: " + missing.joinToString(", "),
        )
    }
}
