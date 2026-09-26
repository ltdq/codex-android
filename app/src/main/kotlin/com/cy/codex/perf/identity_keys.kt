package com.cy.codex.perf

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Stable per-instance key for lazy layouts: content-derived keys collide on equal items and an
 * index is not identity (the front of a bounded list is evicted). [IdentityHashMap] supplies the
 * identity; stale entries are pruned once the map outgrows the live set.
 */
class IdentityKeys<T : Any> {
    private val ids = IdentityHashMap<T, Int>()
    private var nextId = 0

    fun keyOf(value: T, live: Collection<T>): Int {
        if (ids.size > live.size * 2 + PruneSlack) prune(live)
        return ids.getOrPut(value) { nextId++ }
    }

    private fun prune(live: Collection<T>) {
        if (live.isEmpty()) {
            ids.clear()
            return
        }
        val identities: MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
        identities.addAll(live)
        val iterator = ids.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() !in identities) iterator.remove()
        }
    }

    private companion object {
        /** Growth allowed past the live set before a prune is worth its walk. */
        const val PruneSlack = 8
    }
}
