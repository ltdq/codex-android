package com.cy.codex.app

import java.io.File
import java.nio.file.Path

/** Explicit directory decisions override inherited trust, including entries without a trust level. */
internal fun isProjectTrusted(path: String, projectTrust: Map<String, Boolean>): Boolean {
    val target = absoluteProjectPath(path) ?: return false
    val matching = projectTrust.mapNotNull { (key, trusted) ->
        absoluteProjectPath(key)?.takeIf { target.startsWith(it) }?.let { it to trusted }
    }
    val closest = matching.maxOfOrNull { it.first.nameCount } ?: return false
    return matching.filter { it.first.nameCount == closest }.all { it.second }
}

private fun absoluteProjectPath(path: String): Path? = runCatching {
    File(path).takeIf { it.isAbsolute }?.canonicalFile?.toPath()
}.getOrNull()
