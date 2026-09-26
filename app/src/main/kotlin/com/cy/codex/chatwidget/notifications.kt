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

/** Android half of codex-rs/tui/src/chatwidget/notifications.rs: per-type whitelist keyed by the same wire names. */
enum class AgentNotification(val wire: String, @StringRes val labelRes: Int) {
    TurnComplete("agent-turn-complete", R.string.settings_notifications_turn_complete),
    ApprovalRequested("approval-requested", R.string.settings_notifications_approval),
}

sealed interface AgentNotice {
    data class TurnComplete(val threadId: String, val preview: String?) : AgentNotice

    data class Approval(
        val threadId: String,
        val kind: ApprovalNoticeKind,
        val detail: String?,
    ) : AgentNotice
}

enum class ApprovalNoticeKind { Command, FileChange, Elicitation, Other }

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
    NotificationManagerCompat.from(context).notify(
        if (type == AgentNotification.TurnComplete) TurnCompleteId else ApprovalId,
        notification,
    )
}
