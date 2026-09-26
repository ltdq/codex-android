package com.cy.codex.markdown_render

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import com.cy.codex.R
import java.io.File

/** What a markdown link points at; mirrors `codex-rs/tui/src/markdown_render/local_links.rs`: a web
 * URL or a local path with an optional `:line:col` / `#L12C3` location. */
sealed interface LinkTarget {
    data class Web(val url: String) : LinkTarget

    data class Local(val path: String, val location: String?, val display: String) : LinkTarget
}

fun parseLinkTarget(destination: String, cwd: String? = null): LinkTarget {
    val dest = destination.trim()
    if (!isLocalPathLike(dest)) return LinkTarget.Web(dest)
    val (path, location) = splitLocation(dest)
    return LinkTarget.Local(path, location, displayLocalPath(path, cwd) + location.orEmpty())
}

/** The shapes `local_links.rs` treats as a path rather than a URL. */
fun isLocalPathLike(dest: String): Boolean =
    dest.startsWith("file://") ||
        dest.startsWith("/") ||
        dest.startsWith("~/") ||
        dest.startsWith("./") ||
        dest.startsWith("../") ||
        dest.startsWith("\\\\") ||
        dest.length > 2 && dest[0].isLetter() && dest[1] == ':' && (dest[2] == '/' || dest[2] == '\\')

private val HashLocation = Regex("^L\\d+(?:C\\d+)?(?:-L\\d+(?:C\\d+)?)?$")
private val ColonLocation = Regex(":\\d+(?::\\d+)?(?:[-–]\\d+(?::\\d+)?)?$")
private const val CitationOpen = ":codex-file-citation{"
private val CitationPath = Regex("path\\s*=\\s*\"([^\"]*)\"")

/** The path of a `:codex-file-citation{path="…"}` directive starting at [from], if any. */
internal fun citationAt(text: String, from: Int): Pair<Int, String>? {
    if (!text.startsWith(CitationOpen, from)) return null
    val close = text.indexOf('}', from + CitationOpen.length)
    if (close < 0) return null
    val body = text.substring(from + CitationOpen.length, close)
    val path = CitationPath.find(body)?.groupValues?.get(1)
    return if (path.isNullOrEmpty()) null else (close + 1) to path
}

private fun splitLocation(dest: String): Pair<String, String?> {
    val decoded = decodeFileUrl(dest)
    val hash = decoded.substringAfter('#', "")
    if (hash.isNotEmpty() && HashLocation.matches(hash)) {
        return decoded.substringBefore('#') to "#$hash"
    }
    val match = ColonLocation.find(decoded)
    if (match != null) {
        return decoded.substring(0, match.range.first) to match.value
    }
    return decoded to null
}

private fun decodeFileUrl(dest: String): String {
    val stripped = if (dest.startsWith("file://")) dest.removePrefix("file://") else dest
    return decodePercent(stripped.replace('\\', '/'))
}

/** Percent-decode a path without turning `+` into a space, which a query decoder would. */
private fun decodePercent(text: String): String {
    if ('%' !in text) return text
    val bytes = java.io.ByteArrayOutputStream()
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char == '%' && index + 2 < text.length) {
            val value = text.substring(index + 1, index + 3).toIntOrNull(16)
            if (value != null) {
                bytes.write(value)
                index += 3
                continue
            }
        }
        bytes.write(char.toString().toByteArray(Charsets.UTF_8))
        index++
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}

fun displayLocalPath(path: String, cwd: String?): String {
    val normalized = path.replace('\\', '/')
    if (!normalized.startsWith("/") || cwd.isNullOrBlank()) return normalized
    val base = cwd.replace('\\', '/').trimEnd('/')
    if (base.isEmpty() || normalized == base) return normalized
    return if (normalized.startsWith("$base/")) normalized.removePrefix("$base/") else normalized
}

/**
 * A path as a diff row shows it, mirroring `diff_render.rs::display_path_for`: relative as
 * written, cwd-relative, `..`-relative when sharing an ancestor, `~` under the runtime home.
 */
fun displayDiffPath(path: String, cwd: String?, home: String?): String {
    val normalized = path.replace('\\', '/')
    if (!normalized.startsWith("/")) return normalized
    val base = cwd?.replace('\\', '/')?.trimEnd('/').orEmpty()
    if (base.isNotEmpty()) {
        if (normalized == base) return normalized
        if (normalized.startsWith("$base/")) return normalized.removePrefix("$base/")
        relativeTo(normalized, base)?.let { return it }
    }
    val homeBase = home?.replace('\\', '/')?.trimEnd('/').orEmpty()
    if (homeBase.isNotEmpty() && normalized.startsWith("$homeBase/")) {
        return "~/" + normalized.removePrefix("$homeBase/")
    }
    return normalized
}

private fun relativeTo(path: String, base: String): String? {    val pathParts = path.split('/')
    val baseParts = base.split('/')
    var shared = 0
    while (shared < pathParts.size && shared < baseParts.size && pathParts[shared] == baseParts[shared]) {
        shared++
    }
    // Two absolute paths always share the empty root segment; a real ancestor needs one more.
    if (shared <= 1) return null
    val ups = baseParts.size - shared
    return (List(ups) { ".." } + pathParts.drop(shared)).joinToString("/")
}

/**
 * Act on a tapped link: web goes to the platform; a local path has no editor, so the exact target
 * is copied to the clipboard instead.
 */
fun openLink(context: Context, uriHandler: UriHandler, target: LinkTarget) {
    when (target) {
        is LinkTarget.Web -> runCatching { uriHandler.openUri(target.url) }
            .onFailure { Toast.makeText(context, target.url, Toast.LENGTH_SHORT).show() }

        is LinkTarget.Local -> {
            val text = target.path + target.location.orEmpty()
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(text, text))
            Toast.makeText(
                context,
                context.getString(R.string.markdown_link_path_copied, text),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

@Composable
fun runtimeHome(): String {
    val context = LocalContext.current
    return remember(context) { File(context.filesDir, "home").absolutePath }
}
