package com.cy.codex.app

import com.cy.codex.protocol.protocol.v2.Account
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStatusTest {

    @Test
    fun `ChatGPT account combines email and plan`() {
        assertEquals(
            "user@example.com (Plus)",
            statusAccountDisplay(Account.Chatgpt("user@example.com", "plus"), "API key"),
        )
        assertEquals("Plus", statusAccountDisplay(Account.Chatgpt(null, "plus"), "API key"))
        assertEquals("user@example.com", statusAccountDisplay(Account.Chatgpt("user@example.com", ""), "API key"))
        assertEquals("ChatGPT", statusAccountDisplay(Account.Chatgpt(null, ""), "API key"))
    }

    @Test
    fun `only API key and ChatGPT accounts have display values`() {
        assertEquals("API key configured", statusAccountDisplay(Account.ApiKey, "API key configured"))
        assertNull(statusAccountDisplay(Account.AmazonBedrock(), "API key configured"))
        assertNull(statusAccountDisplay(null, "API key configured"))
    }

    @Test
    fun `wire plan names follow the status card labels`() {
        assertEquals("Business Premium", statusAccountDisplay(Account.Chatgpt(null, "self_serve_business_prolite"), ""))
        assertEquals("Business", statusAccountDisplay(Account.Chatgpt(null, "team"), ""))
        assertEquals("Enterprise (Automation)", statusAccountDisplay(Account.Chatgpt(null, "enterprise_cbp_automation"), ""))
        assertEquals("Pro (Max)", statusAccountDisplay(Account.Chatgpt(null, "promax"), ""))
    }
}
