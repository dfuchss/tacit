package org.fuchss.matrix.tacit.views.room.timeline

import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.timeline.ReplyToAreaView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementViewSelector
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.TextReply
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.ReferencedMessagePillView
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedIconButton
import org.fuchss.matrix.tacit.views.common.TacitUserAvatar
import de.connect2x.trixnity.messenger.compose.view.theme.messengerColors
import de.connect2x.trixnity.messenger.viewmodel.UserInfoElement
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.RoomMessageTimelineElementViewModel
import org.fuchss.matrix.tacit.tacit
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import kotlin.time.TimeMark
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Lines of a quoted text message: in the timeline and in the composer's reply bar. */
internal const val TacitReplyLinesInTimeline = 3
internal const val TacitReplyLinesInComposer = 2

/**
 * `true` inside the composer's reply bar: the bar already is the tinted card and names the sender
 * ("Replying to …"), so the quote pill only contributes the quoted content there.
 */
internal val LocalTacitReplyInComposer = staticCompositionLocalOf { false }

/**
 * The key of the timeline element that was just jumped to from a reply quote; its row lights up
 * briefly (see TacitFlatMessageView). Provided by TacitTimelineView.
 */
internal val LocalTacitJumpHighlightKey = compositionLocalOf<String?> { null }

/**
 * Written when a reply quote is pressed. The timeline only highlights a scroll target that follows
 * such a press, so programmatic scrolls (opening a room, jump to the end) never flash a row.
 */
internal class TacitReplyJumpSignal {
    private var lastPress: TimeMark? = null

    fun press() {
        lastPress = TimeSource.Monotonic.markNow()
    }

    /** Whether a quote was pressed just before (the jump may have to load the timeline first); resets it. */
    fun consumeRecentPress(): Boolean {
        val press = lastPress ?: return false
        lastPress = null
        return press.elapsedNow() < 6.seconds
    }
}

internal val LocalTacitReplyJumpSignal = staticCompositionLocalOf<TacitReplyJumpSignal?> { null }

/** Inner padding of the quote card; the start leaves room for the inset sender-coloured bar. */
private val ReplyBarInset = 6.dp
private val ReplyBarWidth = 3.dp
private val ReplyContentStart = ReplyBarInset + ReplyBarWidth + 8.dp

/** Resting tint and border of a quote card, as alphas of its colour. */
internal const val TacitQuoteTintAlpha = 0.10f
internal const val TacitQuoteBorderAlpha = 0.16f

@Composable
private fun replySenderColor(sender: UserInfoElement?): Color =
    sender?.let { MaterialTheme.messengerColors.getUserColor(it.userId, MaterialTheme.tacit.surfaceAlt) } ?: MaterialTheme.tacit.textMuted

/**
 * Tacit's quote card, shared by reply quotes, the composer's reply bar and formatted blockquotes: a compact
 * rounded card tinted with [background], a hairline [border] and the inset rounded bar in [bar].
 */
internal fun Modifier.tacitQuoteCard(background: Color, border: Color, bar: Color): Modifier =
    clip(TacitShapes.compact)
        .background(background, TacitShapes.compact)
        .border(TacitSizes.borderWidth, border, TacitShapes.compact)
        .replyBar(bar)

/** Inner padding of a [tacitQuoteCard]; the start clears the bar. */
internal fun Modifier.tacitQuoteCardPadding(end: Dp = 10.dp): Modifier =
    padding(start = ReplyContentStart, end = end, top = 8.dp, bottom = 8.dp)

/** The rounded vertical bar in the quoted sender's colour, inset from the card's left edge. */
private fun Modifier.replyBar(color: Color): Modifier = drawBehind {
    val inset = ReplyBarInset.toPx()
    val width = ReplyBarWidth.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(width, (size.height - 2 * inset).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(width / 2),
    )
}

/**
 * Tacit's quote pill (replaces upstream's white-washed `ReferencedMessagePillViewImpl` for every
 * element type): a compact card tinted with the quoted sender's colour, a rounded bar in that colour,
 * and a header with a reply glyph, the sender's avatar and name. Faint at rest, stronger on hover,
 * like the message rows themselves.
 */
internal class TacitReferencedMessagePillView : ReferencedMessagePillView {
    @Composable
    override fun create(
        holder: TimelineElementHolderViewModel,
        element: TimelineElementViewModel<*>,
        modifier: Modifier,
        interactionSource: MutableInteractionSource,
        content: @Composable () -> Unit,
    ) {
        if (LocalTacitReplyInComposer.current) {
            Box(modifier) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.tacit.text.copy(alpha = TacitAlpha.quotedText)) { content() }
            }
            return
        }

        val sender = holder.sender.collectAsState().value
        val senderImage = sender?.image?.collectAsState()?.value
        val senderColor = replySenderColor(sender)
        val isHovered by interactionSource.collectIsHoveredAsState()
        val isFocused by interactionSource.collectIsFocusedAsState()
        val backgroundColor by animateColorAsState(senderColor.copy(alpha = if (isHovered) 0.18f else TacitQuoteTintAlpha))
        val borderColor by animateColorAsState(
            when {
                isFocused -> MaterialTheme.tacit.accent.copy(alpha = TacitAlpha.focusRing)
                isHovered -> senderColor.copy(alpha = 0.42f)
                else -> senderColor.copy(alpha = TacitQuoteBorderAlpha)
            }
        )
        val jumpSignal = LocalTacitReplyJumpSignal.current

        Column(
            modifier = Modifier
                .clip(TacitShapes.compact)
                .pointerInput(jumpSignal) {
                    if (jumpSignal == null) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // Only a completed primary click counts: not a right click, drag or cancelled tap.
                        if (currentEvent.buttons.isPrimaryPressed || down.type != PointerType.Mouse) {
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            if (up != null) jumpSignal.press()
                        }
                    }
                }
                .then(modifier)
                .fillMaxWidth()
                .hoverable(interactionSource)
                .tacitQuoteCard(backgroundColor, borderColor, senderColor)
                .tacitQuoteCardPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = senderColor.copy(alpha = 0.8f),
                )
                if (sender != null) {
                    TacitUserAvatar(
                        initials = sender.initials,
                        image = senderImage,
                        size = 16.dp,
                    )
                }
                Text(
                    text = sender?.name ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = senderColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.tacit.text.copy(alpha = TacitAlpha.quotedText)) { content() }
        }
    }
}

/**
 * Quoted text, upright and clamped to [maxLines] lines. Plain bodies (no HTML) are a single text
 * with an ellipsis; formatted bodies keep upstream's rich rendering (mentions, links, inline
 * images), which clips at [maxLines] lines instead of ellipsising.
 */
@Composable
internal fun TacitTextReply(element: RoomMessageTimelineElementViewModel.TextBased<*>, maxLines: Int) {
    if (element.formattedBody != null) {
        TextReply(element, maxLines)
    } else {
        Text(
            text = element.body,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Normal,
            color = LocalContentColor.current,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The composer's reply bar (replaces upstream `ReplyToArea`): the same sender-tinted card as the
 * quote in the timeline, an accent reply icon, "Replying to <name>", a two-line quote and a cancel
 * button. Its horizontal padding matches the input row below, so both share one edge.
 */
internal class TacitReplyToAreaView : ReplyToAreaView {
    @Composable
    override fun ColumnScope.create(inputAreaViewModel: InputAreaViewModel) {
        TacitReplyToArea(inputAreaViewModel)
    }
}

@Composable
private fun ColumnScope.TacitReplyToArea(inputAreaViewModel: InputAreaViewModel) {
    val i18n = DI.get<TacitI18nView>()
    val timelineElementViewSelector = DI.get<TimelineElementViewSelector>()
    val repliedElementHolder = inputAreaViewModel.repliedElement.collectAsState().value
    val element = repliedElementHolder?.element?.collectAsState()?.value

    // Remembered so the exit animation still shows the last quote instead of empty content.
    val last = remember { arrayOfNulls<Any>(2) }
    if (repliedElementHolder != null && element != null) {
        last[0] = repliedElementHolder
        last[1] = element
    }
    val shownHolder = (repliedElementHolder ?: last[0]) as TimelineElementHolderViewModel?
    val shownElement = (element ?: last[1]) as TimelineElementViewModel<*>?

    AnimatedVisibility(element != null, enter = fadeIn() + slideInVertically(initialOffsetY = { 200 })) {
        if (shownHolder == null || shownElement == null) return@AnimatedVisibility
        val sender = shownHolder.sender.collectAsState().value
        val senderColor = replySenderColor(sender)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                .tacitQuoteCard(senderColor.copy(alpha = TacitQuoteTintAlpha), senderColor.copy(alpha = 0.22f), senderColor)
                .tacitQuoteCardPadding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = i18n.replyTo(),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.tacit.accent,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = buildAnnotatedString {
                        append(i18n.tacitReplyingTo())
                        withStyle(MaterialTheme.typography.labelMedium.toSpanStyle().copy(fontWeight = FontWeight.SemiBold, color = senderColor)) {
                            append(sender?.name ?: "")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.tacit.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CompositionLocalProvider(LocalTacitReplyInComposer provides true) {
                    timelineElementViewSelector.createReplyInSendMessage(shownHolder, shownElement)
                }
            }
            Tooltip(tooltip = { Text(i18n.replyToCancel()) }) {
                ThemedIconButton(
                    style = MaterialTheme.components.commonIconButton,
                    onClick = { inputAreaViewModel.cancelReply() },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = i18n.replyToCancel(),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.tacit.textMuted,
                    )
                }
            }
        }
    }
}
