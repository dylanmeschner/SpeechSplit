package no.srrlsm.speechsplit.core

import no.srrlsm.speechsplit.jvm.DocumentReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Importing a speech document and suggesting segments. Plain JVM tests. */
class SpeechImportTest {
    private fun words(n: Int, word: String = "Word") = List(n) { word }.joinToString(" ") + "."

    private fun suggest(draft: ImportDraft, wpm: Int = 130, total: Int? = null, parts: Int? = null) =
        suggestSegments(draft, wpm, total, parts, "Introduction") { "Part $it" }

    @Test
    fun headingsBecomeSegments() {
        val text = """
            Sunday talk

            Introduction
            ${words(130)}

            1. Main point
            ${words(260)}

            Conclusion:
            ${words(65)}
        """.trimIndent()
        val blocks = blocksFromPlainText(text)
        val result = suggest(ImportDraft("x", blocks))
        assertTrue(result.fromHeadings)
        assertEquals(listOf("Introduction", "Main point", "Conclusion"), result.segments.map { it.title })
        // 130 words at 130 wpm = 60 s
        assertEquals(listOf(60, 120, 30), result.segments.map { it.seconds })
    }

    @Test
    fun fixedTotalAddsUpExactly() {
        val blocks = listOf(
            DocBlock("A", true), DocBlock(words(101)),
            DocBlock("B", true), DocBlock(words(77)),
            DocBlock("C", true), DocBlock(words(333)),
        )
        for (total in listOf(1200, 1203, 61, 599)) {
            val result = suggest(ImportDraft("x", blocks), total = total)
            assertEquals(total, result.totalSeconds)
            assertTrue(result.segments.all { it.seconds > 0 })
        }
    }

    @Test
    fun noHeadingsSplitsEvenly() {
        val paragraphs = List(12) { "Paragraph $it has some text. " + words(80) }
        val blocks = paragraphs.map { DocBlock(it) }
        val result = suggest(ImportDraft("x", blocks), parts = 4)
        assertFalse(result.fromHeadings)
        assertEquals(4, result.segments.size)
        assertEquals(blocks.sumOf { countWords(it.text) }, result.segments.sumOf { it.words })
        // Equal paragraphs: equal parts
        assertTrue(result.segments.all { it.words == result.segments[0].words })
    }

    @Test
    fun oneLongParagraphIsSplitBySentence() {
        val text = List(40) { "This is sentence number $it in a long speech." }.joinToString(" ")
        val result = suggest(ImportDraft("x", blocksFromPlainText(text)), parts = 3)
        assertEquals(3, result.segments.size)
    }

    @Test
    fun neverMorePartsThanText() {
        val result = suggest(ImportDraft("x", listOf(DocBlock("Short speech, just one sentence."))), parts = 8)
        assertEquals(1, result.segments.size)
    }

    @Test
    fun pdfLineBreaksAreJoined() {
        val blocks = blocksFromPlainText("This is a line that was\nbroken by the page, and a hyph-\nenated word continues.")
        assertEquals(1, blocks.size)
        assertEquals("This is a line that was broken by the page, and a hyphenated word continues.", blocks[0].text)
    }

    @Test
    fun readsWordDocumentWithHeadings() {
        val xml = """<?xml version="1.0"?><w:document><w:body>
            <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>My talk</w:t></w:r></w:p>
            <w:p><w:pPr><w:pStyle w:val="Overskrift1"/></w:pPr><w:r><w:t>Innledning</w:t></w:r></w:p>
            <w:p><w:r><w:t xml:space="preserve">Hei alle </w:t></w:r><w:r><w:t>sammen &amp; velkommen.</w:t></w:r></w:p>
            <w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>Hoveddel</w:t></w:r></w:p>
            <w:p><w:r><w:t>Her kommer poenget.</w:t></w:r></w:p>
            <w:p/>
        </w:body></w:document>"""
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val blocks = DocumentReader.read("tale.docx", bytes, pdfToText = null)
        assertNotNull(blocks)
        assertEquals(
            listOf(
                DocBlock("My talk", true), DocBlock("Innledning", true), DocBlock("Hei alle sammen & velkommen."),
                DocBlock("Hoveddel", true), DocBlock("Her kommer poenget."),
            ),
            blocks,
        )
        val result = suggest(ImportDraft("x", blocks!!))
        assertEquals(listOf("Innledning", "Hoveddel"), result.segments.map { it.title })
    }

    @Test
    fun unknownBinaryIsUnsupported() {
        val bytes = ByteArray(200) { (it % 7).toByte() }
        assertEquals(null, DocumentReader.read("picture.png", bytes, pdfToText = null))
    }

    @Test
    fun rtfBecomesText() {
        val rtf = "{\\rtf1\\ansi{\\fonttbl\\f0 Arial;}\\f0 Overskrift\\par\\par Dette er f\\'f8rste avsnitt med tekst i talen.\\par}"
        val blocks = DocumentReader.read("a.rtf", rtf.toByteArray(), null)!!
        assertTrue(blocks.any { it.text.contains("første avsnitt") })
    }
}
