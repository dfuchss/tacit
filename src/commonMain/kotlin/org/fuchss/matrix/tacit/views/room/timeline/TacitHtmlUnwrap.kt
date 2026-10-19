package org.fuchss.matrix.tacit.views.room.timeline

import de.connect2x.trixnity.messenger.util.html.HtmlNode

/** The only child of this element that is not [ignorable], if it is an element; `null` otherwise. */
internal fun HtmlNode.HtmlElement.soleElementChild(ignorable: (HtmlNode) -> Boolean): HtmlNode.HtmlElement? =
    children.filterNot(ignorable).singleOrNull() as? HtmlNode.HtmlElement

internal fun HtmlNode.isBlankText(): Boolean = this is HtmlNode.TextContent && content.isBlank()
