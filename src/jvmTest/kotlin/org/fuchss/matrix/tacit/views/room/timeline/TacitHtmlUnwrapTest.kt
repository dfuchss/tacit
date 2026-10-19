package org.fuchss.matrix.tacit.views.room.timeline

import de.connect2x.trixnity.messenger.util.html.HtmlNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TacitHtmlUnwrapTest {
    private fun el(tag: String, vararg children: HtmlNode) = HtmlNode.HtmlElement(tag, emptyMap(), children.toList())
    private fun text(value: String) = HtmlNode.TextContent(value)

    @Test
    fun `returns the single element child ignoring blank text`() {
        val inner = el("p")
        assertSame(inner, el("div", text("\n"), inner, text("  ")).soleElementChild(HtmlNode::isBlankText))
    }

    @Test
    fun `returns null for several children, text child or none`() {
        assertNull(el("div", el("p"), el("p")).soleElementChild(HtmlNode::isBlankText))
        assertNull(el("div", text("hi")).soleElementChild(HtmlNode::isBlankText))
        assertNull(el("div").soleElementChild(HtmlNode::isBlankText))
    }

    @Test
    fun `custom ignorable predicate skips more nodes`() {
        val inner = el("p")
        val root = el("div", el("mx-reply"), inner)
        assertNull(root.soleElementChild(HtmlNode::isBlankText))
        assertSame(inner, root.soleElementChild { it is HtmlNode.HtmlElement && it.tag == "mx-reply" })
    }

    @Test
    fun `isBlankText only matches whitespace text`() {
        assertTrue(text(" \n").isBlankText())
        assertEquals(false, text("a").isBlankText())
        assertEquals(false, el("p").isBlankText())
    }
}
