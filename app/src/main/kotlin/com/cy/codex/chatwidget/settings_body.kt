package com.cy.codex.chatwidget

import androidx.compose.runtime.Composable
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.SessionState
import com.cy.codex.protocol.protocol.v2.ConfigSnapshot
import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a settings page reads and the ways it acts.
 *
 * One value is threaded to every page instead of a dozen parameters, because a page is copy plus a
 * few controls and every control needs one of these. Pages never talk to the server themselves: a
 * write goes through [write], which is `config/value/write` against the user's own `config.toml`.
 */
class SettingsPageContext(
    val catalog: CatalogState,
    val session: SessionState,
    val configPath: String,
    val ordinaryUsageRecovered: Boolean,
    val onEvent: (AppEvent) -> Unit,
    val onOpenEntry: (String) -> Unit,
    val onOpenWorkspacePicker: () -> Unit,
    val onOpenShortcuts: () -> Unit,
) {
    /** The effective `config.toml`, as [ConfigSnapshot] projects it. */
    val config: ConfigSnapshot get() = catalog.configSnapshot

    /** The open session's `thread/…` state: what the session rows report. */
    val thread: ThreadSessionState get() = session.config

    /** The model preset of the open session, or the catalog's default when it names none. */
    val preset: ModelPreset?
        get() =
            catalog.modelPreset(thread.model)
                ?: catalog.models.firstOrNull { it.isDefault && !it.hidden }

    /** Write one key of the user's `config.toml`, which the server applies on the next read. */
    fun write(keyPath: String, value: JsonElement) {
        onEvent(AppEvent.WriteConfigValue(keyPath, value))
    }

    fun write(keyPath: String, value: Boolean) = write(keyPath, JsonPrimitive(value))

    fun write(keyPath: String, value: String) = write(keyPath, JsonPrimitive(value))
}

/**
 * The body of one settings page.
 *
 * The pages live in the `settings_*.kt` files beside this one, one file per group of the desktop's
 * sidebar, and each is a `CodexPage` body: the frame and the title are the caller's.
 */
@Composable
internal fun settingsPage(context: SettingsPageContext, section: SettingsSection) {
    when (section) {
        SettingsSection.General -> SettingsGeneralPage(context)
        SettingsSection.Import -> SettingsImportPage(context)
        SettingsSection.Appearance -> SettingsAppearancePage(context)
        SettingsSection.Voice -> SettingsVoicePage(context)
        SettingsSection.Agent -> SettingsAgentPage(context)
        SettingsSection.Personalization -> SettingsPersonalizationPage(context)
        SettingsSection.Pets -> SettingsPetsPage(context)
        SettingsSection.Shortcuts -> SettingsShortcutsPage(context)
        SettingsSection.Plugins -> SettingsPluginsPage(context)
        SettingsSection.Computer -> SettingsComputerPage(context)
        SettingsSection.Snapshots -> SettingsSnapshotsPage(context)
        SettingsSection.Browser -> SettingsBrowserPage(context)
        SettingsSection.Hooks -> SettingsHooksPage(context)
        SettingsSection.Connections -> SettingsConnectionsPage(context)
        SettingsSection.Review -> SettingsReviewPage(context)
        SettingsSection.Git -> SettingsGitPage(context)
        SettingsSection.Environment -> SettingsEnvironmentPage(context)
        SettingsSection.Worktrees -> SettingsWorktreesPage(context)
        SettingsSection.ArchivedChats -> SettingsArchivedChatsPage(context)
    }
}
