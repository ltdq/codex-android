package com.cy.codex

import com.cy.codex.protocol.protocol.Json
import com.cy.codex.protocol.protocol.v2.Account
import com.cy.codex.protocol.protocol.v2.AccountReadResponse
import com.cy.codex.protocol.protocol.v2.ConfigReadResponse
import com.cy.codex.protocol.protocol.v2.ConfigRequirementsReadResponse
import com.cy.codex.protocol.protocol.v2.ForcedLoginMethod
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginMethodsTest {
    @Test
    fun `login requirements distinguish unrestricted and empty policies`() {
        assertNull(ConfigRequirementsReadResponse().allowedLoginMethods)
        assertEquals(
            emptyList(),
            ConfigRequirementsReadResponse(Json.parse("""{"allowedLoginMethods":[]}""")).allowedLoginMethods,
        )
        assertEquals(
            listOf(ForcedLoginMethod.Api, ForcedLoginMethod.Chatgpt),
            ConfigRequirementsReadResponse(
                Json.parse("""{"allowedLoginMethods":["api","chatgpt"]}""")
            ).allowedLoginMethods,
        )
    }

    @Test
    fun `login methods follow the effective server policy`() {
        val catalog = CatalogState()
        assertTrue(catalog.isLoginMethodAllowed(ForcedLoginMethod.Api))
        assertTrue(catalog.isLoginMethodAllowed(ForcedLoginMethod.Chatgpt))

        catalog.allowedLoginMethods = listOf(ForcedLoginMethod.Api)
        assertTrue(catalog.isLoginMethodAllowed(ForcedLoginMethod.Api))
        assertFalse(catalog.isLoginMethodAllowed(ForcedLoginMethod.Chatgpt))

        catalog.allowedLoginMethods = emptyList()
        assertFalse(catalog.isLoginMethodAllowed(ForcedLoginMethod.Api))
        assertFalse(catalog.isLoginMethodAllowed(ForcedLoginMethod.Chatgpt))
    }

    @Test
    fun `bedrock setup requires eligible sign-in state`() {
        val catalog = CatalogState()
        catalog.account = AccountReadResponse(requiresOpenaiAuth = true)
        catalog.config = ConfigReadResponse(
            config = Json.parse("""{"features":{"bedrock_setup_wizard":true}}""")
        )
        assertTrue(catalog.shouldShowBedrockSetupWizard)

        catalog.allowedLoginMethods = listOf(ForcedLoginMethod.Chatgpt)
        assertFalse(catalog.shouldShowBedrockSetupWizard)
        catalog.allowedLoginMethods = listOf(ForcedLoginMethod.Api)
        assertTrue(catalog.shouldShowBedrockSetupWizard)

        catalog.config = ConfigReadResponse(
            config = Json.parse("""{"model_provider":"openai","features":{"bedrock_setup_wizard":true}}""")
        )
        assertFalse(catalog.shouldShowBedrockSetupWizard)
        catalog.config = ConfigReadResponse(
            config = Json.parse("""{"features":{"bedrock_setup_wizard":false}}""")
        )
        assertFalse(catalog.shouldShowBedrockSetupWizard)

        catalog.config = ConfigReadResponse(
            config = Json.parse("""{"features":{"bedrock_setup_wizard":true}}""")
        )
        catalog.account = AccountReadResponse(requiresOpenaiAuth = false)
        assertFalse(catalog.shouldShowBedrockSetupWizard)
        catalog.account = AccountReadResponse(requiresOpenaiAuth = true, account = Account.ApiKey)
        assertFalse(catalog.shouldShowBedrockSetupWizard)
    }
}
