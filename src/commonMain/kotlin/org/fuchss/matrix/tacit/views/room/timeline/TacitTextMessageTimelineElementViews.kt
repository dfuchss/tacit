package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.Platform
import de.connect2x.trixnity.messenger.compose.view.PlatformType
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.richtext.RichTextColors
import de.connect2x.trixnity.messenger.compose.view.richtext.RichTextDisplay
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.*
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.MessageBubble
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedSelectionContainer
import de.connect2x.trixnity.messenger.compose.view.theme.messengerColors
import de.connect2x.trixnity.messenger.util.UriCaller
import de.connect2x.trixnity.messenger.util.html.HtmlNode
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.RoomMessageTimelineElementViewModel
import kotlin.math.roundToInt

internal data class TacitStandaloneSpoiler(
    val reason: String?,
)

private val standaloneSpoilerWrapperTags = setOf("#root", "html", "body", "div", "p")

internal fun extractStandaloneSpoiler(document: HtmlNode.HtmlElement): TacitStandaloneSpoiler? {
    val normalizedChildren = document.children.filterNotBlankTextNodes()
    return when (document.tag) {
        "span" if document.attributes.containsKey("data-mx-spoiler") ->
            TacitStandaloneSpoiler(document.attributes["data-mx-spoiler"]?.ifBlank { null })

        in standaloneSpoilerWrapperTags if normalizedChildren.size == 1 ->
            (normalizedChildren.single() as? HtmlNode.HtmlElement)?.let(::extractStandaloneSpoiler)

        else -> null
    }
}

private fun List<HtmlNode>.filterNotBlankTextNodes(): List<HtmlNode> =
    filterNot { node -> node is HtmlNode.TextContent && node.content.isBlank() }

internal abstract class TacitDelegatingTextTimelineElementView<T : RoomMessageTimelineElementViewModel.TextBased<*>>(
    delegate: TimelineElementView<T>,
) : TimelineElementView<T> by delegate {

    @Composable
    override fun createInTimeline(
        holder: BaseTimelineElementHolderViewModel,
        element: T,
        index: Int,
    ) {
        TacitTextBasedRoomMessageTimelineElementView(holder, element, isPreview = false, index = index)
    }

    @Composable
    override fun createAsPreview(
        holder: TimelineElementHolderViewModel,
        element: T,
        index: Int,
    ) {
        TacitTextBasedRoomMessageTimelineElementView(holder, element, isPreview = true, index = index)
    }
}

internal class TacitTextRoomMessageTimelineElementViewImpl :
    TacitDelegatingTextTimelineElementView<RoomMessageTimelineElementViewModel.TextBased.Text>(
        TextRoomMessageTimelineElementViewImpl()
    ),
    TextRoomMessageTimelineElementView

internal class TacitNoticeRoomMessageTimelineElementViewImpl :
    TacitDelegatingTextTimelineElementView<RoomMessageTimelineElementViewModel.TextBased.Notice>(
        NoticeRoomMessageTimelineElementViewImpl()
    ),
    NoticeRoomMessageTimelineElementView

internal class TacitEmoteRoomMessageTimelineElementViewImpl :
    TacitDelegatingTextTimelineElementView<RoomMessageTimelineElementViewModel.TextBased.Emote>(
        EmoteRoomMessageTimelineElementViewImpl()
    ),
    EmoteRoomMessageTimelineElementView

@Composable
private fun TacitTextBasedRoomMessageTimelineElementView(
    holder: BaseTimelineElementHolderViewModel,
    element: RoomMessageTimelineElementViewModel.TextBased<*>,
    isPreview: Boolean,
    index: Int,
) {
    MessageBubble(
        holder = holder,
        needsMaxWidth = false,
        isPreview = isPreview,
        isMentioned = element.isMentioned,
        index = index,
    ) { showActionMenu ->
        when (Platform.current) {
            PlatformType.DESKTOP, PlatformType.WEB -> ThemedSelectionContainer(
                style = if (holder.isByMe) MaterialTheme.components.selectionOnPrimary
                else MaterialTheme.components.selectionOnSurface
            ) {
                TacitMessageTextContent(holder, element, showActionMenu)
            }

            PlatformType.ANDROID, PlatformType.IOS -> TacitMessageTextContent(holder, element, showActionMenu)
        }
    }
}

@Composable
private fun TacitMessageTextContent(
    holder: BaseTimelineElementHolderViewModel,
    element: RoomMessageTimelineElementViewModel.TextBased<*>,
    showActionMenu: () -> Unit,
) {
    val i18n = DI.get<I18nView>()
    val uriCaller = DI.get<UriCaller>()
    val sender = holder.sender.collectAsState().value
    val standaloneSpoiler = remember(element.formattedBodyContent) {
        element.formattedBodyContent?.let(::extractStandaloneSpoiler)
    }
    val contentModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { showActionMenu() })
    }

    Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp)) {
        if (element is RoomMessageTimelineElementViewModel.TextBased.Emote) {
            Text("${sender?.name}", fontStyle = FontStyle.Italic)
            Spacer(Modifier.size(5.dp))
        }

        if (element is RoomMessageTimelineElementViewModel.TextBased.Notice) {
            // The icon is anchored to the *first* baseline of the body instead of to the top of the row:
            // the first line box grows whenever it contains a mention chip (the chip is an inline
            // placeholder that is centred on the text and much taller than it), which used to push the
            // text down while a top-aligned icon stayed put. Center alignment would be wrong as soon as
            // the notice spans more than one line, so we align by the first line's text instead.
            val opticalCenterAboveBaseline = rememberOpticalCenterAboveBaseline(MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = i18n.automated(),
                    modifier = Modifier
                        .alignBy { it.measuredHeight / 2 + opticalCenterAboveBaseline }
                        .size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                TacitMessageBodyContent(
                    holder = holder,
                    element = element,
                    standaloneSpoiler = standaloneSpoiler,
                    uriCaller = uriCaller,
                    modifier = contentModifier.alignBy(FirstBaseline),
                )
            }
        } else {
            TacitMessageBodyContent(
                holder = holder,
                element = element,
                standaloneSpoiler = standaloneSpoiler,
                uriCaller = uriCaller,
                modifier = contentModifier,
            )
        }
    }
}

/**
 * Distance in pixels between the baseline of a line of [style] text and the optical center of that text
 * (the middle between the ascent and the descent). Aligning the center of a leading icon this far above the
 * body's first baseline keeps the icon visually centered on the first line, no matter how tall that line box
 * becomes because of inline content such as mention chips, and it tracks font scaling and theme typography
 * instead of a hardcoded offset.
 */
@Composable
private fun rememberOpticalCenterAboveBaseline(style: TextStyle): Int {
    val textMeasurer = rememberTextMeasurer()
    return remember(textMeasurer, style) {
        // RichTextDisplay lays its paragraphs out with the line height dropped, so the line box of a plain
        // line of text is exactly ascent + descent; measure the same way to get comparable metrics.
        val measured = textMeasurer.measure(text = "Hg", style = style.copy(lineHeight = TextUnit.Unspecified))
        val ascent = measured.firstBaseline
        val descent = measured.size.height - measured.firstBaseline
        ((ascent - descent) / 2f).roundToInt()
    }
}

@Composable
private fun TacitMessageBodyContent(
    holder: BaseTimelineElementHolderViewModel,
    element: RoomMessageTimelineElementViewModel.TextBased<*>,
    standaloneSpoiler: TacitStandaloneSpoiler?,
    uriCaller: UriCaller,
    modifier: Modifier = Modifier,
) {
    if (standaloneSpoiler != null) {
        TacitSpoilerText(
            text = element.body,
            modifier = modifier,
        )
    } else {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(color = LocalContentColor.current)
        ) {
            RichTextDisplay(
                document = element.formattedBodyContent ?: HtmlNode.HtmlElement("#root", emptyMap(), emptyList()),
                mentions = element.mentionsInFormattedBody,
                modifier = modifier,
                colors = RichTextColors.default(
                    linkColor = if (holder.isByMe) MaterialTheme.messengerColors.linkByMe
                    else MaterialTheme.messengerColors.link
                ),
                onCopy = null,
                onLinkClick = { uriCaller.invoke(it, true) },
                onMentionClick = element::openMention,
            )
        }
    }
}

@Composable
private fun TacitSpoilerText(
    text: String,
    modifier: Modifier = Modifier,
) {
    var revealed by rememberSaveable(text) { mutableStateOf(false) }
    val blurRadius by animateDpAsState(if (revealed) 0.dp else 8.dp)
    val spoilerColor = if (revealed) Color.Unspecified else MaterialTheme.colorScheme.onSurface

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = spoilerColor,
        modifier = modifier
            .pointerInput(revealed) {
                detectTapGestures(onTap = { revealed = !revealed })
            }
            .blur(blurRadius),
    )
}
