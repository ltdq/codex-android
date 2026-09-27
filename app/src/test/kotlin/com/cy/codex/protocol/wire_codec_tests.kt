package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.Json
import com.cy.codex.protocol.protocol.objectValue
import com.cy.codex.protocol.protocol.v2.ActivePermissionProfile
import com.cy.codex.protocol.protocol.v2.BackendBannerCta
import com.cy.codex.protocol.protocol.v2.BannerPresentation
import com.cy.codex.protocol.protocol.v2.WorkspaceMessageType
import com.cy.codex.protocol.protocol.item.AgentMessageDelivery
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.FunctionCallOutputItem
import com.cy.codex.protocol.protocol.item.HookPromptItem
import com.cy.codex.protocol.protocol.item.ImageGenerationFailure
import com.cy.codex.protocol.protocol.item.ImageGenerationItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.WebSearchAction
import com.cy.codex.protocol.protocol.item.WebSearchItem
import com.cy.codex.protocol.protocol.v2.CommandAction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `item` branches fed the app-server's shapes (app-server-protocol/src/protocol/v2/item.rs). */
class WireCodecTest {
    private fun item(json: String) = WireCodec.item(Json.parse(json))

    @Test
    fun `hook prompt fragments carry the run id, not a name`() {
        val parsed = item(
            """{"type":"hookPrompt","id":"h","fragments":[
                 {"text":"Retry with tests.","hookRunId":"hook-run-1"}]}"""
        )
        val fragments = assertIs<HookPromptItem>(parsed).fragments
        assertEquals(1, fragments.size)
        assertEquals("hook-run-1", fragments[0].hookRunId)
        assertEquals("Retry with tests.", fragments[0].text)
    }

    @Test
    fun `web search keeps result elements naming a known field and drops the rest`() {
        val parsed = item(
            """{"type":"webSearch","id":"w","query":"kotlin","action":{"type":"search","query":"kotlin"},
                 "results":[
                   {"type":"text_result","ref_id":"turn0search0","url":"https://example.com/a","title":"A","snippet":"s",
                    "future_field":"ignored"},
                   {"type":"text_result","ref_id":"only-an-id"},
                   {"url":"only-a-url"},
                   {"snippet":"only-a-snippet"},
                   {"title":""},
                   {"future_field":{"preserved":true}},
                   {},
                   "not-an-object"]}"""
        )
        val search = assertIs<WebSearchItem>(parsed)
        val results = search.results!!
        assertEquals(5, results.size, "an element must name at least one of title/url/snippet/ref_id to be kept")
        assertEquals("turn0search0", results[0].refId)
        assertEquals("A", results[0].title)
        assertEquals("https://example.com/a", results[0].url)
        assertEquals("s", results[0].snippet)
        assertEquals("text_result", results[0].type)
        assertEquals("only-an-id", results[1].refId)
        assertEquals("only-a-url", results[2].url)
        assertEquals("only-a-snippet", results[3].snippet)
        assertEquals("", results[4].title, "a blank title is still a named field, unlike an object naming none")
        assertEquals(WebSearchAction.Search(query = "kotlin", queries = null), search.action)
    }

    @Test
    fun `web search keeps a missing results payload distinct from an explicit empty list`() {
        assertNull(
            assertIs<WebSearchItem>(item("""{"type":"webSearch","id":"w","query":"k"}""")).results,
            "an older server sends no results payload at all",
        )
        assertNull(
            assertIs<WebSearchItem>(item("""{"type":"webSearch","id":"w","query":"k","results":null}""")).results,
        )
        assertEquals(
            emptyList(),
            assertIs<WebSearchItem>(item("""{"type":"webSearch","id":"w","query":"k","results":[]}""")).results,
            "an explicit empty list is a search that found nothing",
        )
    }

    @Test
    fun `command execution parses every command action and the plugin attribution`() {
        val parsed = item(
            """{"type":"commandExecution","id":"c","command":"cat a && rg b && ls",
                 "cwd":"/w","status":"completed","source":"agent","pluginId":"superpowers",
                 "scriptPath":"scripts/x.sh",
                 "commandActions":[
                   {"type":"read","command":"cat a","name":"a","path":"/w/a"},
                   {"type":"listFiles","command":"ls"},
                   {"type":"search","command":"rg b","query":"b","path":"/w"},
                   {"type":"unknown","command":"???"},
                   {"type":"futureAction","command":"x"}]}"""
        )
        val exec = assertIs<CommandExecutionItem>(parsed)
        val actions = exec.commandActions
        assertEquals(4, actions.size, "an unknown future action tag is dropped, not guessed")
        assertEquals(CommandAction.Read("cat a", "a", "/w/a"), actions[0])
        assertEquals(CommandAction.ListFiles("ls", null), actions[1])
        assertEquals(CommandAction.Search("rg b", "b", "/w"), actions[2])
        assertEquals(CommandAction.Unknown("???"), actions[3])
        assertEquals("superpowers", exec.pluginId)
        assertEquals("scripts/x.sh", exec.scriptPath)
    }

    @Test
    fun `image generation reads the fields the union actually has`() {
        val parsed = item(
            """{"type":"imageGeneration","id":"i","status":"failed","result":"",
                 "revisedPrompt":"a red cube","transparentBackground":true,
                 "savedPath":"/tmp/image.png",
                 "failure":{"type":"usageLimitExceeded","limitId":"imagegen","resetsAt":1760000000}}"""
        )
        val image = assertIs<ImageGenerationItem>(parsed)
        assertTrue(image.failed)
        assertEquals("a red cube", image.revisedPrompt)
        assertTrue(image.transparentBackground == true)
        assertEquals("/tmp/image.png", image.savedPath)
        val failure = assertIs<ImageGenerationFailure.UsageLimitExceeded>(image.failure)
        assertEquals("imagegen", failure.limitId)
        assertEquals(1760000000L, failure.resetsAt)
    }

    @Test
    fun `image generation without a revised prompt falls back to its id`() {
        val image = assertIs<ImageGenerationItem>(item("""{"type":"imageGeneration","id":"i","status":"completed","result":"data:image/png;base64,AA"}"""))
        assertFalse(image.failed)
        assertNull(image.failure)
        assertEquals("i", image.detail)
    }

    @Test
    fun `mcp tool call keeps app context, app ui and the read only hint`() {
        val parsed = item(
            """{"type":"mcpToolCall","id":"m","server":"s","tool":"t","status":"completed",
                 "arguments":{},"readOnlyHint":true,"pluginId":"p","mcpAppResourceUri":"ui://legacy",
                 "appContext":{"connectorId":"c","appName":"App","actionName":"Do"},
                 "mcpAppUi":{"resourceUri":"ui://new","preferredModelDisplayMode":"fullscreen"}}"""
        )
        val call = assertIs<McpToolCallItem>(parsed)
        assertEquals("c", call.appContext?.connectorId)
        assertEquals("App", call.appContext?.appName)
        assertEquals("Do", call.appContext?.actionName)
        assertNull(call.appContext?.linkId)
        assertEquals("fullscreen", call.mcpAppUi?.preferredModelDisplayMode)
        assertEquals("ui://new", call.appResourceUri, "the descriptor-captured uri wins over the legacy field")
        assertTrue(call.readOnlyHint == true)
        assertEquals("p", call.pluginId)
    }

    @Test
    fun `mcp tool call falls back to the legacy resource uri and tolerates no app context`() {
        val call = assertIs<McpToolCallItem>(item("""{"type":"mcpToolCall","id":"m","server":"s","tool":"t","status":"failed","arguments":{},"mcpAppResourceUri":"ui://legacy"}"""))
        assertNull(call.appContext)
        assertNull(call.mcpAppUi)
        assertEquals("ui://legacy", call.appResourceUri)
        assertNull(call.readOnlyHint)
    }

    @Test
    fun `agent message keeps the memory citation and delivery`() {
        val parsed = item(
            """{"type":"agentMessage","id":"a","text":"done","delivery":"async",
                 "memoryCitation":{"entries":[{"path":"/w/m.md","lineStart":3,"lineEnd":9,"note":"why"},
                                              {"note":"no path"}],
                                   "threadIds":["t1"]}}"""
        )
        val message = assertIs<AgentMessageItem>(parsed)
        assertEquals(AgentMessageDelivery.Async, message.delivery)
        val citation = message.memoryCitation
        assertEquals(1, citation?.entries?.size, "a citation entry without a path is dropped")
        assertEquals("/w/m.md", citation?.entries?.first()?.path)
        assertEquals(3, citation?.entries?.first()?.lineStart)
        assertEquals(9, citation?.entries?.first()?.lineEnd)
        assertEquals("why", citation?.entries?.first()?.note)
        assertEquals(listOf("t1"), citation?.threadIds)
    }

    @Test
    fun `agent message without a citation or delivery leaves both null`() {
        val message = assertIs<AgentMessageItem>(item("""{"type":"agentMessage","id":"a","text":"hi"}"""))
        assertNull(message.memoryCitation)
        assertNull(message.delivery)
    }

    @Test
    fun `unknown item types stay inspectable instead of dropping the turn`() {
        val parsed = item("""{"type":"brandNewThing","id":"x","payload":1}""")
        val fallback = assertIs<FunctionCallOutputItem>(parsed)
        assertEquals("x", fallback.id)
        assertEquals("brandNewThing", fallback.name)
        assertTrue(fallback.output.contains("\"payload\""), "the raw item is kept so an unknown type is still visible")
    }

    @Test
    fun `session keeps the active permission profile and tolerates its absence`() {
        val decoded = WireCodec.session(
            Json.parse(
                """{"thread":{"id":"t","cwd":"/w"},"model":"gpt-5","modelProvider":"openai",
                   "activePermissionProfile":{"id":"work","extends":"base"}}""",
            ).objectValue(),
        )
        assertEquals(ActivePermissionProfile("work", "base"), decoded.activePermissionProfile)

        assertNull(
            WireCodec.session(
                Json.parse("""{"thread":{"id":"t","cwd":"/w"},"model":"gpt-5","modelProvider":"openai"}""").objectValue(),
            ).activePermissionProfile,
        )
        assertNull(
            WireCodec.session(
                Json.parse("""{"thread":{"id":"t"},"model":"m","modelProvider":"openai","activePermissionProfile":null}""").objectValue(),
            ).activePermissionProfile,
        )
        assertNull(
            WireCodec.session(
                Json.parse("""{"thread":{"id":"t"},"model":"m","modelProvider":"openai","activePermissionProfile":{}}""").objectValue(),
            ).activePermissionProfile,
            "an object without an id cannot name a profile",
        )
    }

    @Test
    fun `workspace messages keep the feature flag and convert seconds to millis`() {
        val decoded = WireCodec.workspaceMessages(
            Json.parse(
                """{"featureEnabled":true,"messages":[
                   {"messageId":"m1","messageType":"headline","messageBody":"Deploy freeze","createdAt":5,"archivedAt":7},
                   {"messageId":"m2","messageType":"announcement","messageBody":"Lunch"},
                   {"messageId":"m3","messageType":"future-kind","messageBody":"?"}]}""",
            ).objectValue(),
        )
        assertTrue(decoded.featureEnabled)
        val headline = decoded.messages[0]
        assertEquals("m1", headline.messageId)
        assertEquals(WorkspaceMessageType.Headline, headline.messageType)
        assertEquals("Deploy freeze", headline.messageBody)
        assertEquals(5_000L, headline.createdAt)
        assertEquals(7_000L, headline.archivedAt)
        assertEquals(WorkspaceMessageType.Announcement, decoded.messages[1].messageType)
        assertEquals(WorkspaceMessageType.Unknown, decoded.messages[2].messageType)

        val disabled = WireCodec.workspaceMessages(
            Json.parse("""{"featureEnabled":false,"messages":[{"messageId":"m","messageType":"headline","messageBody":"x"}]}""").objectValue(),
        )
        assertFalse(disabled.featureEnabled, "the flag is the degradation switch, not decoration")
        assertTrue(
            WireCodec.workspaceMessages(Json.parse("""{"messages":[]}""").objectValue()).featureEnabled,
            "a payload missing the required flag still scans its messages",
        )
    }

    private fun rateLimits(json: String) = WireCodec.accountRateLimits(Json.parse(json).objectValue())

    private fun upsell(banner: String) =
        rateLimits("""{"rateLimits":{},"rateLimitUpsell":$banner}""").rateLimitUpsell

    @Test
    fun `rate limit upsell decodes the snake_case banner contract`() {
        val banner = upsell(
            """{"banner_type":"selected_model_limit","title":"Selected model usage exhausted",
               "description":"Switch to another model or contact your owner.",
               "ctas":[{"action":"notify_owner","label":"Notify owner"},
                       {"action":"request_increase","label":"Request increase"}],
               "reset_at":1760000000,"model_slug":"test-model-a","blocked_model_slug":"test-model-a",
               "fallback_model_slugs":["model-b","model-c"],"presentation":"dismissible",
               "request_url":"https://example.test/limits"}""",
        )!!
        assertEquals("selected_model_limit", banner.bannerType)
        assertEquals("Selected model usage exhausted", banner.title)
        assertEquals("Switch to another model or contact your owner.", banner.description)
        assertEquals(
            listOf(BackendBannerCta("notify_owner", "Notify owner"), BackendBannerCta("request_increase", "Request increase")),
            banner.ctas,
        )
        assertEquals(1_760_000_000_000L, banner.resetAt, "reset_at is Unix seconds on the wire")
        assertEquals("test-model-a", banner.modelSlug)
        assertEquals("test-model-a", banner.blockedModelSlug)
        assertEquals(listOf("model-b", "model-c"), banner.fallbackModelSlugs)
        assertEquals(BannerPresentation.Dismissible, banner.presentation)
        assertEquals("https://example.test/limits", banner.requestUrl)

        val plain = upsell("""{"banner_type":"b","title":"t","description":"d","ctas":[]}""")!!
        assertEquals(BannerPresentation.Inline, plain.presentation, "presentation defaults to inline")
        assertNull(plain.resetAt)
        assertNull(plain.modelSlug)
        assertNull(plain.blockedModelSlug)
        assertEquals(emptyList(), plain.fallbackModelSlugs)
        assertNull(plain.requestUrl)
    }

    @Test
    fun `a banner failing the parse bounds is dropped whole`() {
        val fourLines = "l1\\nl2\\nl3\\nl4"
        val thirteenLines = (1..13).joinToString("\\n") { "line$it" }
        val controlSlug = "a\\u0007b"
        val tooManyCtas = (1..9).joinToString(",") { """{"action":"a","label":"l"}""" }
        val tooManySlugs = (1..17).joinToString(",") { "\"slug$it\"" }
        val cases = mapOf(
            "blank title" to """{"banner_type":"b","title":"  ","description":"d","ctas":[]}""",
            "title over three lines" to """{"banner_type":"b","title":"$fourLines","description":"d","ctas":[]}""",
            "title over 1024 bytes" to """{"banner_type":"b","title":"${"x".repeat(1025)}","description":"d","ctas":[]}""",
            "description over twelve lines" to """{"banner_type":"b","title":"t","description":"$thirteenLines","ctas":[]}""",
            "description over 4096 bytes" to """{"banner_type":"b","title":"t","description":"${"x".repeat(4097)}","ctas":[]}""",
            "more than eight ctas" to """{"banner_type":"b","title":"t","description":"d","ctas":[$tooManyCtas]}""",
            "more than sixteen fallback slugs" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"fallback_model_slugs":[$tooManySlugs]}""",
            "empty fallback slug" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"fallback_model_slugs":[""]}""",
            "fallback slug of spaces" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"fallback_model_slugs":["  "]}""",
            "fallback slug with a control char" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"fallback_model_slugs":["$controlSlug"]}""",
            "fallback slug over 256 bytes" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"fallback_model_slugs":["${"y".repeat(257)}"]}""",
            "blocked slug with a control char" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"blocked_model_slug":"$controlSlug"}""",
            "ctas of the wrong type" to """{"banner_type":"b","title":"t","description":"d","ctas":"nope"}""",
            "a cta missing its label" to """{"banner_type":"b","title":"t","description":"d","ctas":[{"action":"a"}]}""",
            "unknown presentation" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"presentation":"popup"}""",
            "presentation of the wrong type" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"presentation":1}""",
            "reset_at of the wrong type" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"reset_at":"soon"}""",
            "an optional slug of the wrong type" to """{"banner_type":"b","title":"t","description":"d","ctas":[],"model_slug":7}""",
            "a banner that is not an object" to "[]",
        )
        for ((name, banner) in cases) {
            assertNull(upsell(banner), name)
        }

        val fullCtas = (1..8).joinToString(",") { """{"action":"a","label":"l"}""" }
        val fullSlugs = (1..16).joinToString(",") { "\"ok$it\"" }
        assertNotNull(
            upsell(
                """{"banner_type":"b","title":"${"t".repeat(1024)}","description":"${"d".repeat(4096)}",
                   "ctas":[$fullCtas],
                   "blocked_model_slug":"${"s".repeat(256)}",
                   "fallback_model_slugs":[$fullSlugs]}""",
            ),
            "a banner at every bound still parses",
        )
    }

    @Test
    fun `raw upsell presence survives parse failure`() {
        val invalid = """{"banner_type":"b","title":"  ","description":"d","ctas":[]}"""
        val dropped = rateLimits("""{"rateLimits":{},"rateLimitUpsell":$invalid,"ordinaryUsageAllowed":true}""")
        assertNull(dropped.rateLimitUpsell)
        assertTrue(dropped.rateLimitUpsellPresent, "the wire field is present even though parsing failed")
        val absent = rateLimits("""{"rateLimits":{},"ordinaryUsageAllowed":true}""")
        assertFalse(absent.rateLimitUpsellPresent)
        val nulled = rateLimits("""{"rateLimits":{},"rateLimitUpsell":null,"ordinaryUsageAllowed":true}""")
        assertFalse(nulled.rateLimitUpsellPresent, "an explicit null is Option::None upstream")
        val valid = rateLimits("""{"rateLimits":{},"rateLimitUpsell":{"banner_type":"b","title":"t","description":"d","ctas":[]}}""")
        assertTrue(valid.rateLimitUpsellPresent)
        assertNotNull(valid.rateLimitUpsell)
    }
}
