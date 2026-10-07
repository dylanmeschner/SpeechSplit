package no.srrlsm.speechsplit.jvm

import no.srrlsm.speechsplit.core.DocBlock
import no.srrlsm.speechsplit.core.blocksFromPlainText
import no.srrlsm.speechsplit.core.cleanHeading
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * Reads the text of a speech document. Shared by Android and Windows.
 * Word (.docx) and OpenDocument (.odt) files are zip files with XML inside, so they are read
 * here directly, keeping the document's own headings. PDF needs a library, which differs per
 * platform, so the caller passes in [pdfToText].
 */
object DocumentReader {
    /** Biggest file we try to read (a speech is rarely more than a few hundred KB). */
    const val MAX_BYTES = 30 * 1024 * 1024

    enum class Kind { PDF, DOCX, ODT, TEXT, RTF, UNKNOWN }

    fun kindOf(fileName: String, bytes: ByteArray): Kind {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() -> Kind.PDF
            ext == "pdf" -> Kind.PDF
            ext == "docx" || ext == "docm" -> Kind.DOCX
            ext == "odt" -> Kind.ODT
            ext == "rtf" -> Kind.RTF
            ext in setOf("txt", "text", "md", "markdown") -> Kind.TEXT
            // A zip without a known extension: look inside
            bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> when {
                zipEntry(bytes, "word/document.xml") != null -> Kind.DOCX
                zipEntry(bytes, "content.xml") != null -> Kind.ODT
                else -> Kind.UNKNOWN
            }
            looksLikeText(bytes) -> Kind.TEXT
            else -> Kind.UNKNOWN
        }
    }

    /**
     * Returns the blocks of the document, an empty list if it has no readable text
     * (e.g. a scanned PDF), or null if the file type isn't supported.
     */
    fun read(fileName: String, bytes: ByteArray, pdfToText: ((ByteArray) -> String)?): List<DocBlock>? =
        when (kindOf(fileName, bytes)) {
            Kind.PDF -> pdfToText?.let { blocksFromPlainText(it(bytes)) }
            Kind.DOCX -> readDocx(bytes)
            Kind.ODT -> readOdt(bytes)
            Kind.RTF -> blocksFromPlainText(rtfToText(decode(bytes)))
            Kind.TEXT -> blocksFromPlainText(decode(bytes))
            Kind.UNKNOWN -> null
        }

    /** UTF-8, or Windows-1252 for older text files that aren't valid UTF-8. */
    fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        val text = if (utf8.contains('�')) String(bytes, Charset.forName("windows-1252")) else utf8
        return text.removePrefix("﻿")
    }

    private fun looksLikeText(bytes: ByteArray): Boolean {
        val sample = bytes.take(4096)
        if (sample.isEmpty()) return false
        val controls = sample.count { b -> val v = b.toInt() and 0xFF; v < 9 || (v in 14..31) }
        return controls * 100 / sample.size < 2
    }

    private fun zipEntry(bytes: ByteArray, name: String): ByteArray? = try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == name) return@use zip.readBytes()
                e = zip.nextEntry
            }
            null
        }
    } catch (e: Exception) {
        null
    }

    // --- Word (.docx) ---------------------------------------------------------
    private val wParagraph = Regex("<w:p[ >].*?</w:p>|<w:p/>", RegexOption.DOT_MATCHES_ALL)
    private val wStyle = Regex("<w:pStyle w:val=\"([^\"]*)\"")
    private val wOutline = Regex("<w:outlineLvl w:val=\"(\\d)\"")
    private val wPiece = Regex("<w:t(?: [^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br(?: [^>]*)?/>")

    fun readDocx(bytes: ByteArray): List<DocBlock>? {
        val xml = zipEntry(bytes, "word/document.xml")?.toString(Charsets.UTF_8) ?: return null
        val blocks = mutableListOf<DocBlock>()
        for (m in wParagraph.findAll(xml)) {
            val p = m.value
            val text = wPiece.findAll(p).joinToString("") { piece ->
                when {
                    piece.value.startsWith("<w:tab") -> " "
                    piece.value.startsWith("<w:br") -> " "
                    else -> unescapeXml(piece.groupValues[1])
                }
            }.replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) continue
            val style = wStyle.find(p)?.groupValues?.get(1)?.lowercase() ?: ""
            val heading = wOutline.containsMatchIn(p) || isHeadingStyle(style)
            blocks += if (heading) DocBlock(cleanHeading(text), heading = true) else DocBlock(text)
        }
        return mergeHeadingRuns(blocks)
    }

    /** Heading styles in English, Norwegian and German Word ("Heading1", "Overskrift1", "berschrift1", "Title"…). */
    private fun isHeadingStyle(style: String): Boolean =
        style.startsWith("heading") || style.startsWith("overskrift") || style.contains("berschrift") ||
            style == "title" || style == "tittel" || style == "titel" || style.startsWith("subtitle")

    // --- OpenDocument (.odt) --------------------------------------------------
    private val odtBlock = Regex("<text:(h|p)(?: [^>]*)?(?:/>|>(.*?)</text:\\1>)", RegexOption.DOT_MATCHES_ALL)

    fun readOdt(bytes: ByteArray): List<DocBlock>? {
        val xml = zipEntry(bytes, "content.xml")?.toString(Charsets.UTF_8) ?: return null
        val body = xml.substringAfter("<office:text", xml)
        val blocks = mutableListOf<DocBlock>()
        for (m in odtBlock.findAll(body)) {
            val inner = m.groupValues[2]
                .replace(Regex("<text:(tab|line-break|s)[^>]*/>"), " ")
                .replace(Regex("<[^>]+>"), "")
            val text = unescapeXml(inner).replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) continue
            blocks += if (m.groupValues[1] == "h") DocBlock(cleanHeading(text), heading = true) else DocBlock(text)
        }
        return mergeHeadingRuns(blocks)
    }

    /** A "heading" that is really a long paragraph (bad styling) counts as text. */
    private fun mergeHeadingRuns(blocks: List<DocBlock>): List<DocBlock> =
        blocks.map { if (it.heading && it.text.length > 120) it.copy(heading = false) else it }

    private fun unescapeXml(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: "" }
            .replace("&amp;", "&")

    // --- RTF (rough: drops formatting, keeps paragraphs) ----------------------
    fun rtfToText(rtf: String): String {
        if (!rtf.trimStart().startsWith("{\\rtf")) return rtf
        val out = StringBuilder()
        var i = 0
        var depth = 0
        var skipDepth = -1
        while (i < rtf.length) {
            val c = rtf[i]
            when {
                c == '{' -> { depth++; i++ }
                c == '}' -> { if (depth == skipDepth) skipDepth = -1; depth--; i++ }
                c == '\\' -> {
                    val m = Regex("^\\\\([a-z]+)(-?\\d+)? ?|^\\\\'([0-9a-fA-F]{2})|^\\\\(.)").find(rtf.substring(i, minOf(rtf.length, i + 40)))
                    if (m == null) { i++; continue }
                    val word = m.groupValues[1]
                    if (skipDepth < 0) when {
                        word in setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer") -> skipDepth = depth
                        word == "par" || word == "line" -> out.append('\n').append(if (word == "par") "\n" else "")
                        word == "tab" -> out.append(' ')
                        m.groupValues[3].isNotEmpty() -> out.append(m.groupValues[3].toInt(16).toChar())
                        m.groupValues[4].isNotEmpty() && m.groupValues[4] in listOf("\\", "{", "}") -> out.append(m.groupValues[4])
                    }
                    i += m.value.length
                }
                else -> { if (skipDepth < 0 && c != '\r' && c != '\n') out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
