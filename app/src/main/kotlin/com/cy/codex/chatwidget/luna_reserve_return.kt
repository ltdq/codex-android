package com.cy.codex.chatwidget

import android.content.SharedPreferences
import com.cy.codex.protocol.protocol.v2.ReasoningEffort

/**
 * The account-bound return target saved before automatic Reserve entry
 * (codex-rs/tui/src/chatwidget/luna_reserve_return.rs); task-local, never the global model default.
 */
data class ReserveReturnModel(
    val accountId: String,
    val model: String,
    val effort: ReasoningEffort?,
)

/** Persistence seam so the cache logic stays Compose-free. */
interface ReserveReturnStore {
    fun load(threadId: String, accountId: String): ReserveReturnModel?
    fun save(threadId: String, entry: ReserveReturnModel)
    fun clearThread(threadId: String)
}

/** Test double and fallback for callers without a context. */
class InMemoryReserveReturnStore : ReserveReturnStore {
    private val entries = mutableMapOf<String, ReserveReturnModel>()

    override fun load(threadId: String, accountId: String): ReserveReturnModel? =
        entries[reserveReturnKey(threadId, accountId)]

    override fun save(threadId: String, entry: ReserveReturnModel) {
        entries[reserveReturnKey(threadId, entry.accountId)] = entry
    }

    override fun clearThread(threadId: String) {
        val prefix = "$ReserveReturnPrefix$threadId/"
        entries.keys.filter { it.startsWith(prefix) }.forEach { entries.remove(it) }
    }
}

/** SharedPreferences cache; keys carry `threadId+accountId`, so another account's target is unreachable. */
class SharedPrefsReserveReturnStore(private val preferences: SharedPreferences) : ReserveReturnStore {
    override fun load(threadId: String, accountId: String): ReserveReturnModel? {
        val raw = preferences.getString(reserveReturnKey(threadId, accountId), null) ?: return null
        return decodeReserveReturn(raw)?.takeIf { it.accountId == accountId }
    }

    override fun save(threadId: String, entry: ReserveReturnModel) {
        preferences.edit()
            .putString(reserveReturnKey(threadId, entry.accountId), encodeReserveReturn(entry))
            .apply()
    }

    override fun clearThread(threadId: String) {
        val prefix = "$ReserveReturnPrefix$threadId/"
        val stale = preferences.all.keys.filter { it.startsWith(prefix) }
        if (stale.isEmpty()) return
        val editor = preferences.edit()
        stale.forEach { editor.remove(it) }
        editor.apply()
    }
}

private const val ReserveReturnPrefix = "luna_reserve_return/"

/** A corrupt or unrelated payload must never cause an unbounded read. */
private const val MaxEntryChars = 4096

/** ASCII unit separator: account ids and slugs are single-line values, so this cannot collide. */
private val FieldSeparator = Char(31)

internal fun reserveReturnKey(threadId: String, accountId: String) =
    "$ReserveReturnPrefix$threadId/$accountId"

internal fun encodeReserveReturn(entry: ReserveReturnModel): String =
    listOf(entry.accountId, entry.model, entry.effort?.wire.orEmpty())
        .joinToString(FieldSeparator.toString())

internal fun decodeReserveReturn(raw: String): ReserveReturnModel? {
    if (raw.length > MaxEntryChars) return null
    val parts = raw.split(FieldSeparator, limit = 3)
    if (parts.size != 3) return null
    val (accountId, model, effort) = parts
    if (accountId.isEmpty() || model.isEmpty()) return null
    val decodedEffort = when {
        effort.isEmpty() -> null
        else -> ReasoningEffort.entries.firstOrNull { it.wire == effort } ?: return null
    }
    return ReserveReturnModel(accountId, model, decodedEffort)
}

/**
 * Saves the return target before any server state change (codex-rs/tui/src/chatwidget/backend_banners.rs
 * `prepare_luna_reserve_return`); failure routes the caller to unavailable recovery.
 */
fun prepareReserveReturn(
    store: ReserveReturnStore,
    threadId: String,
    accountId: String?,
    model: String,
    effort: ReasoningEffort?,
): Boolean {
    val account = accountId ?: return false
    if (threadId.isEmpty() || account.isEmpty()) return false
    return runCatching {
        store.save(threadId, ReserveReturnModel(account, model, effort))
    }.isSuccess
}

/** Account-mismatched entries are refused: recovery is only ever for the account that stored it. */
fun loadReserveReturn(
    store: ReserveReturnStore,
    threadId: String,
    accountId: String?,
): ReserveReturnModel? {
    val account = accountId ?: return null
    return store.load(threadId, account)?.takeIf { it.accountId == account }
}

fun clearReserveReturn(store: ReserveReturnStore, threadId: String) {
    if (threadId.isEmpty()) return
    runCatching { store.clearThread(threadId) }
}

/**
 * Forks copy the parent's return target (codex-rs/tui/src/chatwidget/backend_banners.rs): the
 * parent's recovery deletes the parent's entry; cross-account parents are not inherited.
 */
fun inheritReserveReturn(
    store: ReserveReturnStore,
    threadId: String,
    forkedFromId: String?,
    accountId: String?,
): ReserveReturnModel? {
    val previous = forkedFromId?.let { loadReserveReturn(store, it, accountId) } ?: return null
    return previous.takeIf { prepareReserveReturn(store, threadId, accountId, it.model, it.effort) }
}
