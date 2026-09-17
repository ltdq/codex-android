package com.cy.codexui.protocol

import com.cy.codexui.protocol.protocol.JsonValue
import com.cy.codexui.protocol.protocol.v2.*

internal object WireCatalogCodec {
    fun goal(o: JsonValue.Obj) = ThreadGoalUpdated(o.required("threadId"), o.required("objective"),
        GoalStatus.entries.find { it.wire == o.text("status") } ?: error("Unknown goal status: ${o.text("status")}"), o.int("tokensUsed") ?: 0, o.long("timeUsedSeconds") ?: 0)

    fun queued(o: JsonValue.Obj) = QueuedSubmission(o.required("id"), o.required("clientUserMessageId"), o.array("input").map(WireCodec::input))

    fun project(o: JsonValue.Obj) = ProjectEntry(o.required("id"), o.required("name"),
        o.array("roots").firstOrNull()?.objectValue()?.text("path").orEmpty())

    fun section(o: JsonValue.Obj) = ThreadSection(o.required("id"), o.required("name"))

    fun skill(o: JsonValue.Obj) = SkillEntry(o.text("path") ?: o.required("name"), o.required("name"), o.text("description").orEmpty(),
        o.text("path").orEmpty(), o.bool("enabled") != false, when (o.text("scope")) {
            "repo", "project" -> SkillScope.Project
            "system", "admin" -> SkillScope.System
            else -> SkillScope.User
        })

    fun app(o: JsonValue.Obj) = AppInfo(o.required("id"), o.required("name"), o.text("description").orEmpty(), o.bool("isAccessible") == true)

    fun plugin(o: JsonValue.Obj, marketplace: String) = PluginEntry(o.required("id"), o.required("name"),
        o.objectOrNull("interface")?.text("shortDescription") ?: o.objectOrNull("interface")?.text("description").orEmpty(),
        o.bool("installed") == true, o.text("localVersion") ?: o.text("version").orEmpty(), marketplace)

    fun marketplace(o: JsonValue.Obj): MarketplaceEntry {
        val name = o.required("name")
        val plugins = o.array("plugins").map { plugin(it.objectValue(), name) }
        return MarketplaceEntry(name, o.text("path").orEmpty(), o.text("path") == null, plugins.size,
            description = o.objectOrNull("interface")?.text("description").orEmpty(), plugins = plugins)
    }

    fun marketplaceErrors(o: JsonValue.Obj) = o.array("marketplaceLoadErrors").map { value -> value.objectValue().let {
        MarketplaceLoadErrorInfo(it.text("marketplacePath").orEmpty(), it.text("message").orEmpty())
    } }

    fun pluginDetail(o: JsonValue.Obj): PluginDetail {
        val marketplace = o.required("marketplaceName")
        val summary = plugin(o.objectOrNull("summary") ?: error("Missing plugin summary"), marketplace)
        return PluginDetail(summary.id, summary.name, o.text("description") ?: summary.description, summary.version, marketplace, summary.installed,
            skills = o.array("skills").map { skill(it.objectValue()) },
            mcpServers = o.strings("mcpServers").map { McpServerStatusEntry(it, McpServerConnectionStatus.Starting) },
            apps = o.array("apps").map { app(it.objectValue()) })
    }
}
