package com.cy.codex.app

import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.CollabAgentState
import com.cy.codex.protocol.protocol.v2.CollabAgentTool
import com.cy.codex.protocol.protocol.v2.CollabAgentToolCallStatus
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentRosterTest {

    private val main = "th_main"
    private val sub = "th_sub_1"

    private val MAIN_LABEL = "主代理"
    private val SUB_FORMAT = "子代理 · %s"

    private fun spawn(
        id: String,
        receivers: List<String>,
        prompt: String? = null,
        states: Map<String, CollabAgentState> = emptyMap(),
        model: String? = null,
    ) = CollabAgentToolCallItem(
        id = id,
        tool = CollabAgentTool.SpawnAgent,
        status = CollabAgentToolCallStatus.Completed,
        senderThreadId = main,
        receiverThreadIds = receivers,
        prompt = prompt,
        model = model,
        reasoningEffort = ReasoningEffort.Medium,
        agentsStates = states,
    )

    private fun thread(
        id: String,
        parent: String,
        createdAt: Long,
        name: String? = null,
    ) = Thread(
        id = id,
        preview = "Work on $id",
        modelProvider = "openai",
        createdAt = createdAt,
        updatedAt = createdAt,
        cwd = "/tmp",
        status = ThreadStatus.Idle,
        cliVersion = "0",
        ephemeral = false,
        projectId = null,
        sessionId = id,
        name = name,
        parentThreadId = parent,
    )

    @Test
    fun mainAgentAlwaysComesFirst() {
        val roster = deriveAgentRoster(emptyList(), main, MAIN_LABEL, SUB_FORMAT)
        assertEquals(1, roster.size)
        assertEquals(main, roster[0].threadId)
        assertEquals(AgentRole.Main, roster[0].role)
        assertEquals("主代理", roster[0].name)
        assertNull(roster[0].status)
    }

    @Test
    fun subagentsAppearInFirstAppearanceOrder() {
        val roster = deriveAgentRoster(
            listOf(
                spawn("c1", listOf(sub)),
                spawn("c2", listOf("th_sub_2")),
                spawn("c3", listOf(sub, "th_sub_2")),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertEquals(listOf(main, sub, "th_sub_2"), roster.map { it.threadId })
        assertEquals(listOf(AgentRole.Main, AgentRole.Sub, AgentRole.Sub), roster.map { it.role })
    }

    @Test
    fun activityItemNamesTheAgentFromItsPath() {
        val roster = deriveAgentRoster(
            listOf(
                spawn("c1", listOf(sub)),
                SubAgentActivityItem("a1", SubAgentActivityKind.Started, sub, "/root/status_panel_animation"),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        val entry = roster.first { it.threadId == sub }
        assertEquals("子代理 · status_panel_animation", entry.name)
        assertEquals(SubAgentActivityKind.Started, entry.activity)
    }

    @Test
    fun withoutAPathTheNameFallsBackToTheThreadSuffix() {
        val roster = deriveAgentRoster(listOf(spawn("c1", listOf("th_abcdef"))), main, MAIN_LABEL, SUB_FORMAT)
        assertEquals("子代理 · cdef", roster.first { it.threadId == "th_abcdef" }.name)
    }

    @Test
    fun latestStatusAndActivityWin() {
        val roster = deriveAgentRoster(
            listOf(
                spawn(
                    "c1",
                    listOf(sub),
                    states = mapOf(sub to CollabAgentState(AgentRunStatus.Running)),
                ),
                SubAgentActivityItem("a1", SubAgentActivityKind.Started, sub, "/root/sub"),
                spawn(
                    "c2",
                    listOf(sub),
                    states = mapOf(
                        sub to CollabAgentState(AgentRunStatus.Completed, "已提交 42 行改动"),
                    ),
                ),
                SubAgentActivityItem("a2", SubAgentActivityKind.Completed, sub, "/root/sub"),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        val entry = roster.first { it.threadId == sub }
        assertEquals(AgentRunStatus.Completed, entry.status)
        assertEquals(SubAgentActivityKind.Completed, entry.activity)
    }

    @Test
    fun aLaterInteractionWithoutAPromptKeepsTheSpawnPrompt() {
        val roster = deriveAgentRoster(
            listOf(
                spawn("c1", listOf(sub), prompt = "把状态悬浮窗的展开动效对齐侧栏", model = "gpt-5.1-codex-mini"),
                CollabAgentToolCallItem(
                    id = "c2",
                    tool = CollabAgentTool.Wait,
                    status = CollabAgentToolCallStatus.InProgress,
                    senderThreadId = main,
                    receiverThreadIds = listOf(sub),
                    prompt = null,
                ),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        val entry = roster.first { it.threadId == sub }
        assertEquals("把状态悬浮窗的展开动效对齐侧栏", entry.task)
        assertEquals("gpt-5.1-codex-mini", entry.model)
        assertEquals(ReasoningEffort.Medium, entry.effort)
    }

    @Test
    fun theMainThreadIsNeverAddedTwice() {
        val roster = deriveAgentRoster(
            listOf(
                spawn("c1", listOf(main, sub)),
                SubAgentActivityItem("a1", SubAgentActivityKind.Started, main, "/root/self"),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertEquals(listOf(main, sub), roster.map { it.threadId })
    }

    @Test
    fun nonCollaborationItemsAreIgnored() {
        val roster = deriveAgentRoster(
            listOf(
                UserMessageItem("u1", content = listOf(UserInput.Text("hi"))),
                AgentMessageItem("m1", "hello"),
            ),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertEquals(1, roster.size)
        assertTrue(roster.all { it.role == AgentRole.Main })
    }

    @Test
    fun tokensAreNotInvented() {
        val roster = deriveAgentRoster(listOf(spawn("c1", listOf(sub))), main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertTrue(roster.all { it.tokens == 0 })
    }

    @Test
    fun pagerPagesStartAtTheOpenThreadAndKeepSpawnOrder() {
        val roster = deriveAgentRoster(
            listOf(spawn("c1", listOf(sub)), spawn("c2", listOf("th_sub_2"))),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertEquals(listOf(main, sub, "th_sub_2"), agentPageThreads(main, roster))
    }

    @Test
    fun descendantListingRestoresAgentsMissingFromLoadedParentHistory() {
        val recent = "th_recent"
        val older = "th_older"
        val grandchild = "th_grandchild"
        val transcript = deriveAgentRoster(
            listOf(spawn("recent_spawn", listOf(recent), prompt = "Recent task")),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        val roster = mergeAgentRoster(
            transcript,
            listOf(
                thread(recent, main, createdAt = 30),
                thread(grandchild, older, createdAt = 20, name = "Nested agent"),
                thread(older, main, createdAt = 10, name = "Earlier agent"),
            ),
            SUB_FORMAT,
        )

        assertEquals(listOf(main, older, grandchild, recent), agentPageThreads(main, roster))
        assertEquals("Earlier agent", roster[1].name)
        assertEquals("Nested agent", roster[2].name)
        assertEquals("Recent task", roster[3].task)
        assertEquals(ThreadStatus.Idle, roster[2].threadStatus)
    }

    @Test
    fun recentSpawnRemainsVisibleBeforeTheDescendantListingRefreshes() {
        val roster = deriveAgentRoster(
            listOf(spawn("recent_spawn", listOf(sub))),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        assertEquals(listOf(main, sub), agentPageThreads(main, mergeAgentRoster(roster, emptyList(), SUB_FORMAT)))
    }

    @Test
    fun descendantMetadataDoesNotReorderAgentsAlreadyInTheTranscript() {
        val first = "th_first"
        val second = "th_second"
        val transcript = deriveAgentRoster(
            listOf(spawn("first_spawn", listOf(first)), spawn("second_spawn", listOf(second))),
            main,
            MAIN_LABEL,
            SUB_FORMAT,
        )
        val roster = mergeAgentRoster(
            transcript,
            listOf(
                thread(first, main, createdAt = 40),
                thread(second, main, createdAt = 30),
                thread("th_older", main, createdAt = 10),
            ),
            SUB_FORMAT,
        )

        assertEquals(listOf(main, "th_older", first, second), agentPageThreads(main, roster))
    }
}
