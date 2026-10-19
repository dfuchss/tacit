package org.fuchss.matrix.tacit.views.room.timeline

import org.fuchss.matrix.tacit.ui.TacitAlpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.richtext.RichTextColors
import de.connect2x.trixnity.messenger.compose.view.richtext.RichTextDisplay
import de.connect2x.trixnity.messenger.util.html.HtmlNode
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementMention
import kotlinx.coroutines.flow.StateFlow
import org.fuchss.matrix.tacit.tacit

/*
 * Upstream renders `<blockquote>` inside its internal rich text renderer (trixnity-messenger-compose-view 4.10.3,
 * `richtext/BlockContent.kt`: an elevated M3 Surface with a square bar, wrap-content width), with no DI view, theme
 * component or CompositionLocal to hook. Instead of copying that renderer, Tacit splits the formatted body at its
 * top-level blockquotes: the runs in between still go through upstream's public `RichTextDisplay`, each blockquote
 * becomes a Tacit quote card whose content is rendered the same way (so nested blockquotes nest as cards).
 * Blockquotes below the top level (e.g. inside a list item) keep upstream's look.
 */

internal sealed interface TacitRichTextSegment {
    /** Formatted content without a top-level blockquote, rendered by upstream. */
    data class Rich(val document: HtmlNode.HtmlElement) : TacitRichTextSegment

    /** The content of a top-level blockquote. */
    data class Quote(val document: HtmlNode.HtmlElement) : TacitRichTextSegment
}

/** Wrappers upstream lays out as a plain column, so their only child is effectively top level. */
private val transparentWrapperTags = setOf("#root", "html", "body", "div")

/** Elements upstream renders as nothing. */
private val invisibleTags = setOf("mx-reply", "head")

private fun HtmlNode.isBlank(): Boolean = when (this) {
    is HtmlNode.TextContent -> content.isBlank()
    is HtmlNode.HtmlElement -> tag in invisibleTags
}

/** [children] without whitespace and invisible elements at either end, as a document; `null` if nothing is left. */
private fun trimmedRoot(children: List<HtmlNode>): HtmlNode.HtmlElement? {
    val first = children.indexOfFirst { !it.isBlank() }
    if (first < 0) return null
    val last = children.indexOfLast { !it.isBlank() }
    return HtmlNode.HtmlElement("#root", emptyMap(), children.subList(first, last + 1).toList())
}

/**
 * Splits [document] into rich runs and top-level blockquotes, or `null` if it has no top-level blockquote (it is
 * then rendered by upstream unchanged). Whitespace and invisible elements at the edges of a run or quote are
 * dropped, so the newlines around and inside a `<blockquote>` don't render as empty lines (upstream renders them).
 */
internal fun splitTacitBlockquotes(document: HtmlNode.HtmlElement): List<TacitRichTextSegment>? {
    var container = document
    while (true) {
        val single = container.children.filterNot { it.isBlank() }.singleOrNull() as? HtmlNode.HtmlElement
        if (single == null || single.tag !in transparentWrapperTags) break
        container = single
    }
    val children = container.children
    if (children.none { it is HtmlNode.HtmlElement && it.tag == "blockquote" }) return null

    val segments = mutableListOf<TacitRichTextSegment>()
    val run = mutableListOf<HtmlNode>()
    fun flush() {
        trimmedRoot(run)?.let { segments += TacitRichTextSegment.Rich(it) }
        run.clear()
    }
    for (child in children) {
        if (child is HtmlNode.HtmlElement && child.tag == "blockquote") {
            flush()
            trimmedRoot(child.children)?.let { segments += TacitRichTextSegment.Quote(it) }
        } else {
            run += child
        }
    }
    flush()
    return segments
}

/**
 * Upstream's `RichTextDisplay` with top-level blockquotes drawn as Tacit quote cards (see the file comment).
 * Documents without one take upstream's path unchanged.
 */
@Composable
internal fun TacitRichTextDisplay(
    document: HtmlNode.HtmlElement,
    mentions: StateFlow<Map<String, TimelineElementMention?>>?,
    modifier: Modifier = Modifier,
    colors: RichTextColors = RichTextColors.default(),
    onCopy: ((String) -> Unit)? = null,
    onLinkClick: (String) -> Unit = {},
    onMentionClick: (TimelineElementMention) -> Unit = {},
) {
    val segments = remember(document) { splitTacitBlockquotes(document) }
    if (segments == null) {
        RichTextDisplay(document, mentions, modifier, colors, onCopy, onLinkClick, onMentionClick)
        return
    }
    // Upstream separates blocks by 1em of the current text style.
    val fontSize = LocalTextStyle.current.fontSize
    val blockGap = if (fontSize.isSp) with(LocalDensity.current) { fontSize.toDp() } else 14.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(blockGap)) {
        for (segment in segments) {
            when (segment) {
                is TacitRichTextSegment.Rich ->
                    RichTextDisplay(segment.document, mentions, Modifier, colors, onCopy, onLinkClick, onMentionClick)

                is TacitRichTextSegment.Quote -> TacitBlockquote {
                    TacitRichTextDisplay(segment.document, mentions, Modifier, colors, onCopy, onLinkClick, onMentionClick)
                }
            }
        }
    }
}

/**
 * A blockquote as a full-width Tacit quote card. It has no sender, so the card takes the muted text colour, the
 * same neutral the reply card falls back to for an unknown sender; the quoted text is dimmed like a reply quote's.
 */
@Composable
private fun TacitBlockquote(content: @Composable () -> Unit) {
    val color = MaterialTheme.tacit.textMuted
    val style = LocalTextStyle.current
    Column(
        Modifier
            .fillMaxWidth()
            .tacitQuoteCard(
                background = color.copy(alpha = TacitQuoteTintAlpha),
                border = color.copy(alpha = TacitQuoteBorderAlpha),
                bar = color,
            )
            .tacitQuoteCardPadding(),
    ) {
        val quotedStyle = if (style.color.isSpecified) style.copy(color = style.color.copy(alpha = style.color.alpha * TacitAlpha.quotedText)) else style
        CompositionLocalProvider(LocalTextStyle provides quotedStyle) { content() }
    }
}
