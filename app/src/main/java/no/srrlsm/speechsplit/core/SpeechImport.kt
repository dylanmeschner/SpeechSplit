package no.srrlsm.speechsplit.core

import kotlin.math.roundToInt

// ============================================================================
// IMPORTING A SPEECH FROM A DOCUMENT
// A PDF, Word file or pasted text is turned into a list of blocks (paragraphs and
// headings). From those, segments are suggested: one per heading if the document has
// headings, otherwise parts of about equal length. Times come from the word count and
// a speaking pace, or from a total time the user types in. Plain Kotlin, no platform code.
// Nothing here is ever applied without the user seeing it first.
// ============================================================================

/** One paragraph or heading of an imported document. */
data class DocBlock(val text: String, val heading: Boolean = false)

/** A document that has been read, waiting for the user to accept the suggestion. */
data class ImportDraft(
    val title: String,
    val blocks: List<DocBlock>,
) {
    val totalWords: Int get() = blocks.filterNot { it.heading }.sumOf { countWords(it.text) }
}

/** A suggested segment, with the word count it is based on (shown in the preview). */
data class SuggestedSegment(val title: String, val words: Int, val seconds: Int)

data class SegmentSuggestion(
    val segments: List<SuggestedSegment>,
    /** True when the split follows the document's own headings. */
    val fromHeadings: Boolean,
) {
    val totalSeconds: Int get() = segments.sumOf { it.seconds }
}

fun countWords(text: String): Int = text.split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } }

private val sentenceEnd = Regex("[.!?…;,]$")
private val numbered = Regex("^(\\d{1,2}|[IVXivx]{1,5}|[A-Za-z])[.)]\\s+\\S")
private val markdownHeading = Regex("^#{1,6}\\s+")

/** A short line that reads like a heading: few words, no sentence punctuation at the end. */
fun looksLikeHeading(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty()) return false
    if (markdownHeading.containsMatchIn(t)) return true
    val words = countWords(t)
    if (words == 0 || words > 10 || t.length > 80) return false
    if (sentenceEnd.containsMatchIn(t)) return false
    // Lines that are only a number or a date are page numbers and the like
    if (t.none { it.isLetter() }) return false
    return true
}

/**
 * Turns plain text (pasted text, a .txt file or text pulled out of a PDF) into blocks.
 * Paragraphs are separated by blank lines. A short line on its own, or the first short line
 * of a paragraph, counts as a heading.
 */
fun blocksFromPlainText(text: String): List<DocBlock> {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace('\u000C', '\n')
    val paragraphs = normalized.split(Regex("\n\\s*\n"))
    val blocks = mutableListOf<DocBlock>()
    for (raw in paragraphs) {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) continue
        var rest = lines
        // A heading starts like one, and the text under it starts a new sentence. A line that was only
        // broken by the page width ("This is a line that was / broken by…") is not a heading.
        val first = rest.first()
        val startsLikeHeading = first.first().let { it.isUpperCase() || it.isDigit() || it == '#' }
        val nextStartsSentence = rest.size == 1 || rest[1].first().let { !it.isLetter() || it.isUpperCase() }
        if (startsLikeHeading && nextStartsSentence && looksLikeHeading(first) &&
            (rest.size == 1 || countWords(rest.drop(1).joinToString(" ")) >= 8)
        ) {
            blocks += DocBlock(cleanHeading(rest.first()), heading = true)
            rest = rest.drop(1)
        }
        if (rest.isEmpty()) continue
        // Join lines that were only broken for the page width; a hyphen at a line end joins the word
        val joined = buildString {
            rest.forEachIndexed { i, line ->
                if (i > 0) {
                    if (endsWith("-") && line.firstOrNull()?.isLowerCase() == true) setLength(length - 1) else append(' ')
                }
                append(line)
            }
        }
        blocks += DocBlock(joined)
    }
    return blocks
}

/** Removes Markdown "#" and numbering like "1." from a heading, so the segment name reads cleanly. */
fun cleanHeading(text: String): String =
    text.trim()
        .replace(markdownHeading, "")
        .replace(Regex("^(\\d{1,2}|[IVXivx]{1,5})[.)]\\s+"), "")
        .trim()
        .trimEnd(':')
        .trim()
        .take(60)

/** The first few words of a text, as a name for a part without a heading. */
private fun firstWords(text: String, max: Int = 5): String {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val head = words.take(max).joinToString(" ").trimEnd(',', '.', ';', ':', '!', '?', '–', '-')
    return if (words.size > max) "$head…" else head
}

private class Section(val title: String?, val texts: MutableList<String> = mutableListOf()) {
    val words: Int get() = texts.sumOf { countWords(it) }
    val text: String get() = texts.joinToString(" ")
}

/**
 * Suggests segments for a document.
 * @param wordsPerMinute speaking pace, used when [totalSeconds] is null.
 * @param totalSeconds if set, this total is shared out by word count instead.
 * @param parts how many parts to make when the document has no headings (null = automatic).
 * @param introTitle name for text that comes before the first heading.
 * @param partTitle name for part n when a part has no heading and no text to name it by.
 */
fun suggestSegments(
    draft: ImportDraft,
    wordsPerMinute: Int,
    totalSeconds: Int?,
    parts: Int?,
    introTitle: String,
    partTitle: (Int) -> String,
): SegmentSuggestion {
    val blocks = draft.blocks
    // 1. Sections at headings
    val sections = mutableListOf<Section>()
    var current = Section(null)
    for (b in blocks) {
        if (b.heading) {
            if (current.title != null || current.texts.isNotEmpty()) sections += current
            current = Section(b.text)
        } else {
            current.texts += b.text
        }
    }
    if (current.title != null || current.texts.isNotEmpty()) sections += current

    // A heading followed straight by another heading (e.g. the document title) has no text: drop it.
    // Text before the first heading becomes the introduction, unless it is just a line or two.
    val withText = sections.filter { it.words > 0 }
    val headed = withText.filter { it.title != null }
    val chosen: List<Section>
    val fromHeadings: Boolean
    if (headed.size >= 2) {
        fromHeadings = true
        chosen = withText.filter { it.title != null || it.words >= 15 }
            .map { if (it.title == null) Section(introTitle, it.texts) else it }
            .ifEmpty { withText }
    } else {
        fromHeadings = false
        chosen = splitEvenly(withText.flatMap { it.texts }, wordsPerMinute, totalSeconds, parts)
    }

    val named = chosen.mapIndexed { i, sec ->
        val title = sec.title?.takeIf { it.isNotBlank() }
            ?: firstWords(sec.text).takeIf { it.isNotBlank() }
            ?: partTitle(i + 1)
        title to sec.words
    }
    return SegmentSuggestion(withTimes(named, wordsPerMinute, totalSeconds), fromHeadings)
}

/** No headings: group the paragraphs (or sentences, for one long paragraph) into parts of about equal length. */
private fun splitEvenly(texts: List<String>, wordsPerMinute: Int, totalSeconds: Int?, parts: Int?): List<Section> {
    var pieces = texts.filter { countWords(it) > 0 }
    if (pieces.isEmpty()) return emptyList()
    val totalWords = pieces.sumOf { countWords(it) }
    val minutes = totalSeconds?.div(60.0) ?: (totalWords.toDouble() / wordsPerMinute.coerceAtLeast(1))
    // About one part per three minutes, between 2 and 8
    val wanted = (parts ?: (minutes / 3).roundToInt().coerceIn(2, 8)).coerceAtLeast(1)
    if (pieces.size < wanted) {
        // Too few paragraphs: split into sentences so there is something to divide
        pieces = pieces.flatMap { p -> p.split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() } }
    }
    val n = wanted.coerceAtMost(pieces.size)
    val result = mutableListOf<Section>()
    var cur = Section(null)
    var done = 0
    for ((i, piece) in pieces.withIndex()) {
        cur.texts += piece
        done += countWords(piece)
        val partsLeft = n - result.size - 1
        val piecesLeft = pieces.size - i - 1
        // Close this part once it has reached its share, keeping enough pieces for the rest
        val share = totalWords.toDouble() * (result.size + 1) / n
        if (partsLeft > 0 && (done >= share || piecesLeft == partsLeft)) {
            result += cur
            cur = Section(null)
        }
    }
    if (cur.texts.isNotEmpty()) result += cur
    return result
}

/** Seconds per part, rounded to 5 s. With a fixed total, the rounding is evened out so the parts add up exactly. */
private fun withTimes(parts: List<Pair<String, Int>>, wordsPerMinute: Int, totalSeconds: Int?): List<SuggestedSegment> {
    if (parts.isEmpty()) return emptyList()
    val totalWords = parts.sumOf { it.second }.coerceAtLeast(1)
    val raw = parts.map { (_, words) ->
        if (totalSeconds != null) totalSeconds.toDouble() * words / totalWords
        else words * 60.0 / wordsPerMinute.coerceAtLeast(1)
    }
    val rounded = raw.map { roundTo5(it.roundToInt()).coerceAtLeast(5) }.toMutableList()
    if (totalSeconds != null && totalSeconds > 0) {
        var diff = totalSeconds - rounded.sum()
        // Give or take the difference on the longest parts, 5 s at a time (the last few seconds on the longest)
        val order = rounded.indices.sortedByDescending { rounded[it] }
        var k = 0
        while (diff != 0 && k < 10_000) {
            val i = order[k % order.size]
            val step = when {
                diff >= 5 -> 5
                diff <= -5 -> -5
                else -> diff
            }
            if (rounded[i] + step >= 1) {
                rounded[i] += step
                diff -= step
            }
            k++
        }
    }
    return parts.mapIndexed { i, (title, words) -> SuggestedSegment(title, words, rounded[i]) }
}
