package com.cy.codex.bottom_pane

import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ElicitationAction
import com.cy.codex.protocol.protocol.Json
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.v2.McpApprovalKind
import com.cy.codex.protocol.protocol.v2.McpElicitationField
import com.cy.codex.protocol.protocol.v2.McpElicitationFieldKind
import com.cy.codex.protocol.protocol.v2.McpElicitationRequest
import com.cy.codex.protocol.protocol.v2.McpElicitationSchema
import com.cy.codex.protocol.protocol.v2.McpToolParamDisplay
import com.cy.codex.protocol.protocol.v2.McpToolSuggestion
import com.cy.codex.protocol.protocol.v2.McpToolSuggestionToolType
import com.cy.codex.protocol.protocol.v2.McpToolSuggestionType
import com.cy.codex.protocol.protocol.v2.UserVerificationProof
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Compose-free pins of codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs derivation. */
class McpServerElicitationTest {

    private fun form(
        message: String = "Run the tool?",
        fields: List<McpElicitationField> = emptyList(),
        meta: String? = null,
    ) = McpElicitationRequest.Form(
        serverName = "test",
        message = message,
        requestedSchema = McpElicitationSchema(fields = fields),
        meta = meta?.let { Json.parse(it) },
    )

    private fun choices(payload: McpElicitationRequest.Form): List<ApprovalChoice> {
        val isTool = payload.approval?.kind == McpApprovalKind.McpToolCall
        return approvalCardChoices(payload.approval, isTool)
    }

    // --- __approval derivation ---

    @Test
    fun `tool approval without persist offers allow and cancel`() {
        val payload = form(meta = """{"codex_approval_kind":"mcp_tool_call"}""")
        assertEquals(listOf(ApprovalChoice.Accept, ApprovalChoice.Cancel), choices(payload))
    }

    @Test
    fun `persist choices add the session and always options`() {
        val payload =
            form(meta = """{"codex_approval_kind":"mcp_tool_call","persist":["session","always"]}""")
        assertEquals(
            listOf(
                ApprovalChoice.Accept,
                ApprovalChoice.AcceptSession,
                ApprovalChoice.AcceptAlways,
                ApprovalChoice.Cancel,
            ),
            choices(payload),
        )
    }

    @Test
    fun `a session-only persist hides the always option`() {
        val payload = form(meta = """{"codex_approval_kind":"mcp_tool_call","persist":"session"}""")
        assertEquals(
            listOf(ApprovalChoice.Accept, ApprovalChoice.AcceptSession, ApprovalChoice.Cancel),
            choices(payload),
        )
    }

    @Test
    fun `an always-only persist hides the session option`() {
        val payload = form(meta = """{"codex_approval_kind":"mcp_tool_call","persist":"always"}""")
        assertEquals(
            listOf(ApprovalChoice.Accept, ApprovalChoice.AcceptAlways, ApprovalChoice.Cancel),
            choices(payload),
        )
    }

    @Test
    fun `a message-only form without meta offers allow deny and cancel`() {
        assertEquals(
            listOf(ApprovalChoice.Accept, ApprovalChoice.Decline, ApprovalChoice.Cancel),
            choices(form()),
        )
    }

    @Test
    fun `a tool approval never offers deny`() {
        val payload =
            form(meta = """{"codex_approval_kind":"mcp_tool_call","persist":["session","always"]}""")
        assertTrue(ApprovalChoice.Decline !in choices(payload), "tool cards have no Deny")
    }

    @Test
    fun `a non tool request still honors the persist options`() {
        val payload =
            form(meta = """{"codex_approval_kind":"browser_auth","persist":["session","always"]}""")
        assertEquals(
            listOf(
                ApprovalChoice.Accept,
                ApprovalChoice.AcceptSession,
                ApprovalChoice.AcceptAlways,
                ApprovalChoice.Decline,
                ApprovalChoice.Cancel,
            ),
            choices(payload),
        )
    }

    // --- submit mapping ---

    @Test
    fun `an accept choice answers accept without meta`() {
        val answer = approvalAnswer(ApprovalChoice.Accept)
        assertEquals(ElicitationAction.Accept, answer.action)
        assertNull(answer.meta)
        assertTrue(answer.content.isEmpty(), "an approval accept never carries content")
    }

    @Test
    fun `the session choice echoes the persist session meta`() {
        val answer = approvalAnswer(ApprovalChoice.AcceptSession)
        assertEquals(ElicitationAction.Accept, answer.action)
        assertEquals(Json.parse("""{"persist":"session"}"""), answer.meta)
        assertTrue(answer.content.isEmpty())
    }

    @Test
    fun `the always choice echoes the persist always meta`() {
        val answer = approvalAnswer(ApprovalChoice.AcceptAlways)
        assertEquals(ElicitationAction.Accept, answer.action)
        assertEquals(Json.parse("""{"persist":"always"}"""), answer.meta)
        assertTrue(answer.content.isEmpty())
    }

    @Test
    fun `the decline choice answers decline`() {
        val answer = approvalAnswer(ApprovalChoice.Decline)
        assertEquals(ElicitationAction.Decline, answer.action)
        assertNull(answer.meta)
        assertTrue(answer.content.isEmpty())
    }

    @Test
    fun `the cancel choice answers cancel`() {
        val answer = approvalAnswer(ApprovalChoice.Cancel)
        assertEquals(ElicitationAction.Cancel, answer.action)
        assertNull(answer.meta)
        assertTrue(answer.content.isEmpty())
    }

    @Test
    fun `an unknown choice value answers cancel`() {
        val answer = approvalAnswer(ApprovalChoice.fromValue("definitely_not_a_choice"))
        assertEquals(ElicitationAction.Cancel, answer.action)
        assertNull(answer.meta)
    }

    @Test
    fun `every choice round trips through its wire value`() {
        for (choice in ApprovalChoice.entries) {
            assertEquals(choice, ApprovalChoice.fromValue(choice.value))
        }
    }

    // --- tool_params_display summary ---

    @Test
    fun `display lines keep at most three params`() {
        val params =
            (1..5).map { index -> McpToolParamDisplay("p$index", JsonPrimitive("v$index"), "P$index") }
        val lines = toolParamDisplayLines(params)
        assertEquals(3, lines.size)
        assertEquals(listOf("P1: v1", "P2: v2", "P3: v3"), lines)
    }

    @Test
    fun `string values collapse whitespace`() {
        val params =
            listOf(McpToolParamDisplay("path", JsonPrimitive("  a\n\tb   c  "), "Path"))
        assertEquals(listOf("Path: a b c"), toolParamDisplayLines(params))
    }

    @Test
    fun `values truncate at sixty graphemes with an ellipsis`() {
        val exact = "x".repeat(60)
        val long = "x".repeat(61)
        assertEquals(exact, formatToolParamValue(JsonPrimitive(exact)), "60 graphemes pass through")
        assertEquals(
            "x".repeat(57) + "...",
            formatToolParamValue(JsonPrimitive(long)),
            "the ellipsis keeps the result inside the 60-grapheme budget",
        )
    }

    @Test
    fun `non string values print as compact json`() {
        val params =
            listOf(
                McpToolParamDisplay("count", JsonPrimitive(3), "Count"),
                McpToolParamDisplay("flag", JsonPrimitive(true), "Flag"),
            )
        assertEquals(listOf("Count: 3", "Flag: true"), toolParamDisplayLines(params))
    }

    @Test
    fun `display lines fall back to sorted tool params`() {
        val withDisplay =
            form(
                meta =
                    """{"codex_approval_kind":"mcp_tool_call","tool_params":{"a":"1"},
                        "tool_params_display":[{"name":"z","value":"9"},{"name":"a","value":"1"}]}"""
                        .trimIndent(),
            )
        assertEquals(
            listOf("z: 9", "a: 1"),
            toolParamDisplayLines(withDisplay.approval!!.displayParams),
            "an explicit display list is never reordered",
        )

        val fallback =
            form(
                meta =
                    """{"codex_approval_kind":"mcp_tool_call","tool_params":{"b":"2","a":"1"}}""",
            )
        assertEquals(
            listOf("a: 1", "b: 2"),
            toolParamDisplayLines(fallback.approval!!.displayParams),
            "without a display list the tool params sort by name",
        )
    }

    // --- form routing ---

    private val suggestionWithUrl =
        """{"codex_approval_kind":"tool_suggestion","tool_type":"connector","suggest_type":"install",""" +
            """"suggest_reason":"Needs the calendar","tool_id":"calendar","tool_name":"Calendar",""" +
            """"install_url":"https://chatgpt.com/apps/calendar"}"""

    private val suggestionWithoutUrl =
        """{"codex_approval_kind":"tool_suggestion","tool_type":"plugin","suggest_type":"enable",""" +
            """"suggest_reason":"Needs the plugin","tool_id":"plugin","tool_name":"Plugin"}"""

    @Test
    fun `a suggestion with an install url routes to the install card`() {
        assertEquals(ElicitationFormKind.Suggestion, elicitationFormKind(form(meta = suggestionWithUrl)))
    }

    @Test
    fun `a suggestion without an install url routes to the plain empty form`() {
        assertEquals(
            ElicitationFormKind.EmptyForm,
            elicitationFormKind(form(meta = suggestionWithoutUrl)),
        )
    }

    @Test
    fun `a message-only form without a suggestion routes to the approval card`() {
        assertEquals(ElicitationFormKind.Approval, elicitationFormKind(form()))
    }

    @Test
    fun `a schema with fields renders the fields form`() {
        val field = McpElicitationField("name", "Name", kind = McpElicitationFieldKind.Text)
        assertEquals(
            ElicitationFormKind.Fields,
            elicitationFormKind(form(fields = listOf(field))),
        )
        assertEquals(
            ElicitationFormKind.Fields,
            elicitationFormKind(form(fields = listOf(field), meta = suggestionWithoutUrl)),
            "a suggestion without a URL is only card-shaped on a message-only schema",
        )
        assertEquals(
            ElicitationFormKind.Suggestion,
            elicitationFormKind(form(fields = listOf(field), meta = suggestionWithUrl)),
            "a suggestion with a URL takes the card whatever the schema holds",
        )
    }

    @Test
    fun `the suggestion model flips is_installed for enable`() {
        val install = form(meta = suggestionWithUrl).approval!!.toolSuggestion!!
        val model = appLinkSuggestion(install)
        assertEquals("Calendar", model.title)
        assertEquals(AppLinkSuggestionInstructions.Install, model.instructions)
        assertEquals(false, model.isInstalled)
        assertEquals("https://chatgpt.com/apps/calendar", model.url)

        val enable = form(meta = suggestionWithoutUrl).approval!!.toolSuggestion!!
        val enabled = appLinkSuggestion(enable)
        assertEquals(AppLinkSuggestionInstructions.Enable, enabled.instructions)
        assertEquals(true, enabled.isInstalled)
        assertNull(enabled.url)
    }

    @Test
    fun `a suggestion model rejects an unopenable install url`() {
        val suggestion =
            McpToolSuggestion(
                toolType = McpToolSuggestionToolType.Connector,
                suggestType = McpToolSuggestionType.Install,
                suggestReason = "reason",
                toolId = "id",
                toolName = "Name",
                installUrl = "http://example.test/app",
            )
        assertNull(appLinkSuggestion(suggestion).url)
    }

    // --- userVerification answers ---

    private val verification =
        McpElicitationRequest.UserVerification(
            serverName = "test",
            title = "Approve purchase",
            description = "Pay 200",
            challenge = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
        )

    @Test
    fun `verification params carry the challenge title and description`() {
        val params = userVerificationVerifyParams(verification)
        assertEquals(verification.challenge, params.challenge)
        assertEquals("Approve purchase", params.title)
        assertEquals("Pay 200", params.description)
    }

    @Test
    fun `a produced proof answers accept with both proof keys`() {
        val answer =
            userVerificationAnswer(UserVerificationProof(credentialId = "cred", signature = "sig"))
        assertEquals(ElicitationAction.Accept, answer.action)
        assertEquals(mapOf("credentialId" to "cred", "signature" to "sig"), answer.content)
        assertNull(answer.meta)
    }

    @Test
    fun `the accept content carries exactly the two proof keys`() {
        val content =
            userVerificationAnswer(UserVerificationProof(credentialId = "c", signature = "s")).content
        assertEquals(setOf("credentialId", "signature"), content.keys)
    }

    @Test
    fun `a missing proof answers cancel`() {
        val answer = userVerificationAnswer(proof = null)
        assertEquals(ElicitationAction.Cancel, answer.action)
        assertTrue(answer.content.isEmpty())
        assertNull(answer.meta)
    }

    // --- per-request form state keying ---

    private fun elicitation(requestId: String, params: McpElicitationRequest) =
        ApprovalRequest.Elicitation(
            requestId = RequestId(requestId),
            threadId = "thread",
            turnId = null,
            itemId = "item",
            receivedAt = 0L,
            params = params,
        )

    @Test
    fun `a submitted first request does not latch an equal queued one`() {
        val first = elicitation("1", form())
        val second = elicitation("2", form())
        assertEquals(first.params, second.params, "the queued payloads really are equal")

        // Compose key/remember keeps state per key; a confirm tap latches only the first key.
        val submittedByStateKey = mutableMapOf<Any, Boolean>()
        submittedByStateKey[elicitationFormStateKey(first)] = true

        assertNull(
            submittedByStateKey[elicitationFormStateKey(second)],
            "the queued request starts unsubmitted or the sheet deadlocks",
        )
        assertEquals(
            true,
            submittedByStateKey[elicitationFormStateKey(elicitation("1", form()))],
            "one request keeps its state across recomposition",
        )
    }

    // --- install card button state ---

    private fun suggestionModel(installUrl: String?): AppLinkSuggestion {
        val suggestion =
            McpToolSuggestion(
                toolType = McpToolSuggestionToolType.Connector,
                suggestType = McpToolSuggestionType.Install,
                suggestReason = "reason",
                toolId = "id",
                toolName = "Name",
                installUrl = installUrl,
            )
        return appLinkSuggestion(suggestion)
    }

    @Test
    fun `a rejected install url disables the install button`() {
        val model = suggestionModel(installUrl = "http://example.test/app")
        assertNull(model.url, "http is rejected by validateAppLinkUrl")
        assertFalse(suggestionPrimaryEnabled(model, AppLinkScreen.Link))
    }

    @Test
    fun `a valid install url keeps the install button enabled`() {
        val model = suggestionModel(installUrl = "https://chatgpt.com/apps/calendar")
        assertEquals("https://chatgpt.com/apps/calendar", model.url)
        assertTrue(suggestionPrimaryEnabled(model, AppLinkScreen.Link))
    }

    @Test
    fun `the confirm and enable steps accept without an openable url`() {
        assertTrue(
            suggestionPrimaryEnabled(suggestionModel(installUrl = null), AppLinkScreen.Confirmation),
        )
        val enable = form(meta = suggestionWithoutUrl).approval!!.toolSuggestion!!
        assertTrue(suggestionPrimaryEnabled(appLinkSuggestion(enable), AppLinkScreen.Link))
    }
}
