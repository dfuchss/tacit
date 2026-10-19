package org.fuchss.matrix.tacit.views.room.timeline

import de.connect2x.trixnity.messenger.util.html.HtmlNode
import de.connect2x.trixnity.messenger.util.html.HtmlVisitor
import org.fuchss.matrix.tacit.views.room.timeline.TacitRichTextSegment.Quote
import org.fuchss.matrix.tacit.views.room.timeline.TacitRichTextSegment.Rich
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TacitBlockquotesTest {
    private fun tags(segment: TacitRichTextSegment): List<String> {
        val document = when (segment) {
            is Rich -> segment.document
            is Quote -> segment.document
        }
        return document.children.map { if (it is HtmlNode.HtmlElement) it.tag else "text:${(it as HtmlNode.TextContent).content}" }
    }

    @Test
    fun `documents without a top-level blockquote are left to upstream`() {
        assertNull(splitTacitBlockquotes(HtmlVisitor.process("<p>hi</p><ul><li><blockquote>deep</blockquote></li></ul>")))
        assertNull(splitTacitBlockquotes(HtmlVisitor.process("plain text")))
    }

    @Test
    fun `splits at top-level blockquotes and trims whitespace at the edges`() {
        val segments = splitTacitBlockquotes(
            HtmlVisitor.process("<blockquote>\n<p>quote</p>\n</blockquote>\n<p>answer</p>\n")
        )!!
        assertEquals(listOf(Quote::class, Rich::class), segments.map { it::class })
        assertEquals(listOf("p"), tags(segments[0]))
        assertEquals(listOf("p"), tags(segments[1]))
    }

    @Test
    fun `drops reply fallbacks and looks through single wrappers`() {
        val segments = splitTacitBlockquotes(
            HtmlVisitor.process("<mx-reply><blockquote>fallback</blockquote></mx-reply><div><blockquote>q</blockquote>after</div>")
        )!!
        assertEquals(listOf(Quote::class, Rich::class), segments.map { it::class })
        assertEquals(listOf("text:after"), tags(segments[1]))
    }

    @Test
    fun `nested blockquotes stay inside the quote for the recursive render`() {
        val segments = splitTacitBlockquotes(
            HtmlVisitor.process("<blockquote>\n<blockquote>\n<p>inner</p>\n</blockquote>\n<p>outer</p>\n</blockquote>")
        )!!
        val quote = segments.single() as Quote
        assertEquals(listOf("blockquote", "text:\n", "p").map { it.replace("\n", " ") }, tags(quote).map { it.replace("\n", " ") })
        val inner = splitTacitBlockquotes(quote.document)!!
        assertEquals(listOf(Quote::class, Rich::class), inner.map { it::class })
    }
}
