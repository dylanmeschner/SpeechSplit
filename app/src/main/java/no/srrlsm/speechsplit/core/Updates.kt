package no.srrlsm.speechsplit.core

// ============================================================================
// UPDATES
// New versions are published as GitHub releases. The app asks GitHub for the
// latest release and compares version numbers. Nothing else is sent.
// ============================================================================

const val GITHUB_REPO = "dylanmeschner/SpeechSplit"
const val WEBSITE_URL = "https://dylanmeschner.github.io/SpeechSplit/"
const val WEB_APP_URL = WEBSITE_URL + "app/"
const val PRIVACY_URL = WEBSITE_URL + "privacy.html"
const val RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"
const val FEEDBACK_EMAIL = "dylan123@live.no"

/** The newest release on GitHub. Download links are null if that file isn't attached. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val windowsUrl: String?,
    val androidUrl: String?,
    /** "2026-10-06", or empty if unknown. */
    val date: String = "",
)

enum class FeedbackKind { BUG, IDEA, OTHER }

/** Loading state of the "Patch notes" list. */
sealed interface NotesState {
    data object Idle : NotesState
    data object Loading : NotesState
    data object Failed : NotesState
    data class Loaded(val releases: List<ReleaseInfo>) : NotesState
}

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

/**
 * Release notes can hold one section per language, each starting with a marker line:
 *   <!-- en -->   <!-- no -->   <!-- de -->
 * (invisible on GitHub). This returns the section for [language] ("en", "no", "de"),
 * falling back to English, then to the first section. Notes without markers are returned as they are.
 */
fun notesForLanguage(markdown: String, language: String): String {
    val marker = Regex("""^\s*<!--\s*(?:lang\s*[:=]\s*)?([a-zA-Z]{2})\s*-->\s*$""")
    val sections = linkedMapOf<String, StringBuilder>()
    var current: StringBuilder? = null
    for (line in markdown.lines()) {
        val m = marker.find(line)
        if (m != null) {
            val code = m.groupValues[1].lowercase().let { if (it == "nb" || it == "nn") "no" else it }
            current = sections.getOrPut(code) { StringBuilder() }
        } else {
            current?.append(line)?.append('\n')
        }
    }
    if (sections.isEmpty()) return markdown
    val text = (sections[language] ?: sections["en"] ?: sections.values.first()).toString()
    // A "---" line between the languages only separates them on GitHub
    return text.lines().filterNot { it.trim().matches(Regex("""-{3,}|\*{3,}|_{3,}""")) }.joinToString("\n").trim()
}

/** Release notes from GitHub in the app's language, with the Markdown symbols removed so they read as plain text. */
fun plainReleaseNotes(markdown: String, language: String = "en"): String =
    notesForLanguage(markdown, language).lines()
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
