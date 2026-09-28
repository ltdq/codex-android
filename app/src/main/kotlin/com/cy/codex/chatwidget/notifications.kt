package com.cy.codex.chatwidget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.cy.codex.MainActivity
import com.cy.codex.R
import com.cy.codex.bottom_pane.truncateGraphemes
import com.cy.codex.protocol.protocol.v2.AsyncUserInputQuestion

/** Android half of codex-rs/tui/src/chatwidget/notifications.rs: per-type whitelist keyed by the same wire names. */
enum class AgentNotification(
    val wire: String,
    @StringRes val labelRes: Int,
    /** Displacing rule for a burst; a turn ending yields to anything that needs the user. */
    val priority: Int,
) {
    TurnComplete("agent-turn-complete", R.string.settings_notifications_turn_complete, 0),
    ApprovalRequested("approval-requested", R.string.settings_notifications_approval, 1),
    PlanModePrompt("plan-mode-prompt", R.string.settings_notifications_plan_mode_prompt, 1),
    AsyncQuestion("async-question", R.string.settings_notifications_async_question, 1),
}

sealed interface AgentNotice {
    data class TurnComplete(val threadId: String, val preview: String?) : AgentNotice

    data class Approval(
        val threadId: String,
        val kind: ApprovalNoticeKind,
        val detail: String?,
    ) : AgentNotice

    /** The prompt's own copy is a resource, so only the thread travels. */
    data class PlanModePrompt(val threadId: String) : AgentNotice

    /** [title] is the named question, empty when the batch has none; [count] words the fallback. */
    data class AsyncQuestion(val threadId: String, val title: String, val count: Int) : AgentNotice
}

/** The whitelist entry this notice is posted under, which also decides its priority. */
internal val AgentNotice.notification: AgentNotification
    get() = when (this) {
        is AgentNotice.TurnComplete -> AgentNotification.TurnComplete
        is AgentNotice.Approval -> AgentNotification.ApprovalRequested
        is AgentNotice.PlanModePrompt -> AgentNotification.PlanModePrompt
        is AgentNotice.AsyncQuestion -> AgentNotification.AsyncQuestion
    }

/** Mirrors `Notification::priority`; only a strictly higher one displaces a pending notice. */
internal val AgentNotice.priority: Int get() = notification.priority

enum class ApprovalNoticeKind { Command, FileChange, Elicitation, Other }

/** `truncate_text(.., 30)` in codex-rs/tui/src/chatwidget/questions.rs. */
private const val QuestionTitleGraphemes = 30

/**
 * The one question a notification can name, empty when the batch has none worth naming
 * (`add_async_questions`, codex-rs/tui/src/chatwidget/questions.rs).
 */
internal fun asyncQuestionNoticeTitle(questions: List<AsyncUserInputQuestion>): String =
    questions.singleOrNull()?.title?.trim().orEmpty()
        .takeIf { it.isNotEmpty() }
        ?.let { truncateGraphemes(it, QuestionTitleGraphemes) }
        .orEmpty()

/** Per-type whitelist in the `codex_ui` preferences; defaults mirror the TUI's Notifications::Enabled(true). */
object NotificationSettings {
    private const val FileName = "codex_ui"
    private const val KeyEnabled = "notifications_enabled"
    private const val KeyTypes = "notification_types"

    var enabled by mutableStateOf(false)
        private set

    var types by mutableStateOf(AgentNotification.entries.toSet())
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KeyEnabled, false)
        types = prefs.getStringSet(KeyTypes, null)
            ?.mapNotNullTo(mutableSetOf()) { wire ->
                AgentNotification.entries.firstOrNull { it.wire == wire }
            }
            ?: AgentNotification.entries.toSet()
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        preferences(context).edit { putBoolean(KeyEnabled, value) }
    }

    fun setType(context: Context, type: AgentNotification, value: Boolean) {
        types = if (value) types + type else types - type
        preferences(context).edit {
            putStringSet(KeyTypes, types.mapTo(mutableSetOf()) { it.wire })
        }
    }

    fun allows(type: AgentNotification): Boolean = enabled && type in types

    private fun preferences(context: Context) =
        context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
}

private const val ChannelId = "codex_agent"
private const val TurnCompleteId = 1001
private const val ApprovalId = 1002
private const val PlanModePromptId = 1003
private const val AsyncQuestionId = 1004

/** One id per type, so a burst of one kind replaces its own notification and leaves the others. */
private fun notificationId(type: AgentNotification): Int = when (type) {
    AgentNotification.TurnComplete -> TurnCompleteId
    AgentNotification.ApprovalRequested -> ApprovalId
    AgentNotification.PlanModePrompt -> PlanModePromptId
    AgentNotification.AsyncQuestion -> AsyncQuestionId
}

fun ensureAgentNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(ChannelId) != null) return
    manager.createNotificationChannel(
        NotificationChannel(
            ChannelId,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        },
    )
}

fun agentNotificationsAllowed(context: Context): Boolean {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** One notification per type, collapsing a burst; opens the app — no per-thread deep link yet. */
@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
fun postAgentNotification(context: Context, type: AgentNotification, body: String) {
    if (!NotificationSettings.allows(type)) return
    if (!agentNotificationsAllowed(context)) return
    ensureAgentNotificationChannel(context)
    val title = context.getString(
        when (type) {
            AgentNotification.TurnComplete -> R.string.notification_turn_complete
            AgentNotification.ApprovalRequested -> R.string.notification_approval_requested
            AgentNotification.PlanModePrompt -> R.string.notification_plan_mode_prompt
            AgentNotification.AsyncQuestion -> R.string.notification_async_question
        },
    )
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pending = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, ChannelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()
    NotificationManagerCompat.from(context).notify(notificationId(type), notification)
}
