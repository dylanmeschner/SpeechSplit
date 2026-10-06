package no.srrlsm.speechsplit.core

// ============================================================================
// UPDATES
// New versions are published as GitHub releases. The app asks GitHub for the
// latest release and compares version numbers. Nothing else is sent.
// ============================================================================

const val GITHUB_REPO = "dylanmeschner/SpeechSplit"
const val WEBSITE_URL = "https://dylanmeschner.github.io/SpeechSplit/"

/** The newest release on GitHub. Download links are null if that file isn't attached. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val windowsUrl: String?,
    val androidUrl: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
}

/**
 * True if [latest] is a higher version than [current], comparing number by number:
 * "2.1" > "2.0", "2.10" > "2.9", "3" > "2.9.9". A development build ("dev") never asks to update.
 */
fun isNewerVersion(latest: String, current: String): Boolean {
    val a = parseVersion(latest) ?: return false
    val b = parseVersion(current) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun parseVersion(v: String): List<Int>? {
    val parts = v.trim().removePrefix("v").removePrefix("V").split(".")
    val nums = parts.map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: return null }
    return nums.ifEmpty { null }
}

/** Release notes from GitHub, with the Markdown symbols removed so they read as plain text. */
fun plainReleaseNotes(markdown: String): String =
    markdown.lines()
        .map { line ->
            line.trim()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^[*-]\\s+"), "• ")
                .replace("**", "")
                .replace("__", "")
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
