package com.cy.codexui.runtime

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.cy.codexui.CodexApp
import com.cy.codexui.ComposerHistory
import com.cy.codexui.NotificationSettings
import com.cy.codexui.ensureAgentNotificationChannel
import com.cy.codexui.protocol.AppServerClient
import com.cy.codexui.protocol.JsonRpcAppServerClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CodexApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Whether any activity is started.
     *
     * Notifications are gated on it the way the TUI gates on terminal focus
     * (`NotificationCondition::Unfocused`): a turn that completes while the user is looking at the
     * transcript needs no alert. Counting starts rather than using a single activity flag keeps a
     * configuration-change recreation from flickering the value.
     */
    var inForeground = false
        private set
    val defaultWorkspace: String get() = File(filesDir, "workspaces/default").absolutePath
    val shellPath: String get() = File(filesDir, "runtime/toolchain/bin/bash").absolutePath
    val configPath: String get() = File(filesDir, "home/.codex/config.toml").absolutePath

    val client: AppServerClient by lazy {
        JsonRpcAppServerClient(NativeRpcTransport(this), appScope, defaultWorkspace)
    }

    // Android owns this instance across Activity recreation; transcript and pending approvals
    // stay attached to the same native session while the process remains alive.
    val app: CodexApp by lazy {
        CodexApp(
            appScope,
            getSharedPreferences("codex", Context.MODE_PRIVATE),
            this,
            client,
            defaultWorkspace,
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationSettings.load(this)
        ComposerHistory.load(this)
        ensureAgentNotificationChannel(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                started++
                inForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) inForeground = false
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
