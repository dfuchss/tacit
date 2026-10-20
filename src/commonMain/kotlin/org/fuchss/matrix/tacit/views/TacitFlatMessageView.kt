package org.fuchss.matrix.tacit.views

import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.timeline.RedactionWarning
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementViewSelector
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.MessageBubbleContent
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.MessageBubbleView
import de.connect2x.trixnity.messenger.compose.view.theme.ComponentStyles
import de.connect2x.trixnity.messenger.compose.view.theme.MaterialThemeComponents
import de.connect2x.trixnity.messenger.compose.view.theme.ThemeComponents
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedProgressIndicator
import org.fuchss.matrix.tacit.views.common.TacitUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OutboxElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.timeline.LocalTacitJumpHighlightKey
import org.fuchss.matrix.tacit.views.room.timeline.rememberJumpHighlightTint

/** Width of the avatar gutter: avatar (30.dp) + the spacer that separates it from the text column. */
private val AvatarGutterWidth = 40.dp

/** Horizontal inset of a message row's card; other full-width timeline rows use it to line up with messages. */
internal fun tacitMessageRowSidePadding(maxWidth: Dp): Dp = if (maxWidth < 400.dp) 10.dp else 20.dp

class TacitFlatMessageView : MessageBubbleView {
    @Composable
    override fun create(
        holder: BaseTimelineElementHolderViewModel,
        needsMaxWidth: Boolean,
        additionalContextActions: @Composable ColumnScope.(onClose: () -> Unit) -> Unit,
        isPreview: Boolean,
        isMentioned: Boolean,
        index: Int,
        content: @Composable (showActionMenu: () -> Unit) -> Unit,
    ) {
        val uiState = rememberTacitFlatMessageUiState(holder)
        TacitFlatMessageContainer(
            holder = holder,
            uiState = uiState,
            needsMaxWidth = needsMaxWidth,
            additionalContextActions = additionalContextActions,
            isPreview = isPreview,
            isMentioned = isMentioned,
            index = index,
            content = content,
        )
    }
}

@Composable
private fun TacitFlatMessageContainer(
    holder: BaseTimelineElementHolderViewModel,
    uiState: TacitFlatMessageUiState,
    needsMaxWidth: Boolean,
    additionalContextActions: @Composable ColumnScope.(onClose: () -> Unit) -> Unit,
    isPreview: Boolean,
    isMentioned: Boolean,
    index: Int,
    content: @Composable (showActionMenu: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val i18n = DI.get<TacitI18nView>()
    val timelineElementViewSelector = DI.get<TimelineElementViewSelector>()
    val element = holder.element.collectAsState().value
    val sender = holder.sender.collectAsState().value

    val menuState = rememberTacitMessageRowMenuState()
    // The row is "active" while it is hovered or while one of its menus is open. Everything that
    // used to be painted permanently (card background, border, quick actions) is bound to this.
    val isRowActive = menuState.rememberIsActive()

    val timelineElementHolder = uiState.timelineElementHolder
    val isOwnMessage = holder.isByMe
    // Previews (e.g. the quoted element of a reply) are rendered out of timeline context, so the
    // grouping signal of the original position must not strip their avatar.
    val isContinuation = uiState.isContinuation && !isPreview
    val canReact = timelineElementHolder?.canBeReactedTo?.collectAsState()?.value == true

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .zIndex(if (isRowActive) 100f else 0f)
            .graphicsLayer { clip = false }
    ) {
        val rowSidePadding = tacitMessageRowSidePadding(maxWidth)
        val density = LocalDensity.current
        // The card treatment is faint at rest and fully shown on hover/active, instead of a permanent
        // per-message frame. `Modifier.background`/`Modifier.border` are draw-only, so changing their
        // alpha keeps the layout byte-for-byte identical and the row cannot jump when the pointer enters.
        //
        // The two send states are the deliberate exception: a message that failed to send, and one
        // that is still on its way out, are painted *permanently* -- a message the server never
        // accepted must not be indistinguishable from one it did, and hover is not a state the user
        // is in while scrolling past.
        //
        // A mention of (or reply to) the current user is a soft accent glow on top of that: a faint
        // tint, a calmer accent border and a few feathered rings just outside the card. Upstream's
        // own solid mention border is switched off in the theme (`mentionBorder`). The send states
        // keep priority, and so does upstream's rule that one's own messages are never highlighted.
        val hasMentionGlow = isMentioned && !isOwnMessage && !uiState.hasSendError && !uiState.isSending
        val mentionGlowColor = MaterialTheme.tacit.accent
        val rowHighlightColor = when {
            uiState.hasSendError -> MaterialTheme.tacit.errorBg
            uiState.isSending -> MaterialTheme.tacit.warningBg
            !isRowActive -> MaterialTheme.tacit.surfaceAlt.copy(alpha = 0.3f)
            isOwnMessage -> MaterialTheme.tacit.surfaceAlt.copy(alpha = 0.88f)
            else -> MaterialTheme.tacit.surfaceAlt.copy(alpha = 0.78f)
        }
        // Upstream's MessageBubbleContent colours the sender name for contrast against the bubble style's
        // colour, which upstream fills with the accent. The row draws Tacit's own card instead, so the
        // contrast base is the resting card over the timeline surface (not the hover state, so the name
        // does not change colour under the pointer).
        val senderNameContrastBase = when {
            uiState.hasSendError -> MaterialTheme.tacit.errorBg
            uiState.isSending -> MaterialTheme.tacit.warningBg
            else -> MaterialTheme.tacit.surfaceAlt.copy(alpha = 0.3f)
        }.compositeOver(MaterialTheme.tacit.surface)
        val rowBorderColor = when {
            uiState.hasSendError -> MaterialTheme.tacit.errorBorder
            uiState.isSending -> MaterialTheme.tacit.warningBorder
            hasMentionGlow -> MaterialTheme.tacit.accent.copy(alpha = if (isRowActive) 0.55f else 0.35f)
            !isRowActive -> MaterialTheme.tacit.border.copy(alpha = 0.25f)
            isOwnMessage -> MaterialTheme.tacit.accent.copy(alpha = 0.56f)
            else -> MaterialTheme.tacit.border.copy(alpha = 0.9f)
        }
        // Consecutive messages of the same sender are pulled together and lose the card's inner
        // breathing room; a sender change or a long pause (showBigGap) opens the group up again.
        val outerTopPadding = when {
            isContinuation -> 2.dp
            uiState.showBigGap -> 14.dp
            else -> 6.dp
        }
        val rowVerticalPadding: Dp = if (isContinuation) 2.dp else 6.dp
        // The target of a jump from a reply quote flashes an accent tint that fades out again.
        val isJumpTarget = !isPreview && LocalTacitJumpHighlightKey.current == holder.key
        // Only a row that is (or just was) the target pays for the animation and the extra layer.
        val jumpTint = rememberJumpHighlightTint(isJumpTarget)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = outerTopPadding)
                .zIndex(if (isRowActive) 10f else 0f)
                .graphicsLayer { clip = false },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = rowSidePadding)
                    .fillMaxWidth()
                    .graphicsLayer { clip = false }
                    .drawBehind { if (hasMentionGlow) drawMentionGlow(mentionGlowColor) }
                    .clip(TacitShapes.card)
                    .background(rowHighlightColor, TacitShapes.card)
                    .then(
                        if (hasMentionGlow) Modifier.background(MaterialTheme.tacit.accent.copy(alpha = if (isRowActive) 0.12f else 0.09f), TacitShapes.card)
                        else Modifier
                    )
                    .then(if (jumpTint != null) Modifier.background(jumpTint, TacitShapes.card) else Modifier)
                    .border(TacitSizes.borderWidth, if (isJumpTarget) MaterialTheme.tacit.accent.copy(alpha = 0.7f) else rowBorderColor, TacitShapes.card)
                    .hoverable(menuState.messageInteractionSource)
                    .pointerInput(holder) {
                        detectTapGestures(onLongPress = { menuState.openActionMenu() })
                    }
                    // Secondary (right) click opens the same menu on desktop. This has to sit
                    // *after* detectTapGestures: within one layout node the Main pass is delivered
                    // innermost-first, so the last pointerInput in the chain sees the event first
                    // and can claim it before the long-press detector consumes the down.
                    //
                    // We deliberately observe the Main pass and bail out on an already consumed
                    // change. Compose's own text context menu (SelectionContainer applies
                    // `showTextContextMenuOnSecondaryClick`, which lives in a *child* layout node)
                    // is therefore served first over selectable message text and keeps its
                    // Copy/Select-all menu; we only open the message menu where it did not claim
                    // the click -- the avatar gutter, the row padding and everything that is not
                    // selectable text.
                    .pointerInput(holder) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.type != PointerEventType.Press) continue
                                if (!event.buttons.isSecondaryPressed) continue
                                val change = event.changes.firstOrNull { !it.isConsumed } ?: continue
                                change.consume()
                                menuState.openActionMenu()
                            }
                        }
                    }
                    .semantics {
                        collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                        // The time is announced for *every* message, including the grouped ones
                        // whose timestamp is only a trailing micro-label visually.
                        this.text = AnnotatedString(
                            "${sender?.name ?: i18n.commonUnknown()} (${holder.formattedTime}): " +
                                    (element?.let { timelineElementViewSelector.a11yLabel(it, i18n) } ?: "") +
                                    // `sendError` is already localized by the view model.
                                    (uiState.sendError?.let { " ($it)" } ?: "")
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = rowVerticalPadding)
            ) {
                if (!isPreview && isRowActive) {
                    TacitFlatMessageHoverActions(
                        holder = holder,
                        uiState = uiState,
                        menuState = menuState,
                        rowSidePadding = rowSidePadding,
                        isOwnMessage = isOwnMessage,
                        canReact = canReact,
                        additionalContextActions = additionalContextActions,
                    )
                }
                if (canReact) {
                    TacitReactionPickerMenu(
                        expanded = menuState.allReactionsOpen,
                        alignEnd = true,
                        yOffset = (-16).dp,
                        onDismiss = { menuState.allReactionsOpen = false },
                        onSelectReaction = { reaction ->
                            timelineElementHolder.addReaction(reaction)
                            menuState.allReactionsOpen = false
                        },
                    )
                }

                Row(verticalAlignment = Alignment.Top) {
                    if (isContinuation) {
                        // Hide the avatar but keep its gutter reserved so the text column of a
                        // grouped run stays on exactly the same left edge. The row already carries
                        // a timestamp at its trailing edge (MessageBubbleContent), so a second,
                        // hover-revealed one in this gutter would only duplicate it.
                        Spacer(Modifier.width(AvatarGutterWidth))
                    } else {
                        TacitUserAvatar(
                            initials = uiState.senderInitials ?: "?",
                            image = uiState.senderAvatar,
                            size = 30.dp,
                            // deliberate optical offset against the first text line, not a bug
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.fillMaxWidth()) {
                        val baseTypography = MaterialTheme.typography
                        // `labelSmall` is what upstream's MessageBubbleContent uses for *both* the
                        // trailing info line (edited marker + time) and the send-error text. On a
                        // failed row the muted timeline grey would bury the error, so it is lifted
                        // to the error foreground there.
                        val labelColor = if (uiState.hasSendError) MaterialTheme.tacit.errorText else MaterialTheme.tacit.textMuted
                        val timelineTypography = remember(baseTypography, labelColor) {
                            baseTypography.copy(
                                labelSmall = baseTypography.labelSmall.copy(color = labelColor),
                            )
                        }
                        MaterialTheme(
                            colorScheme = MaterialTheme.colorScheme,
                            typography = timelineTypography,
                            shapes = MaterialTheme.shapes,
                        ) {
                            // The flat row is not a Surface, so nothing establishes a content
                            // colour for it -- which left upstream's send-error warning icon
                            // (`Icon` tints with LocalContentColor) drawing in the Material
                            // default. Pin it so the icon is actually legible on the dark row.
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.tacit.text) {
                                WithMessageBubbleColor(senderNameContrastBase) {
                                    MessageBubbleContent(
                                        holder = holder,
                                        // Always full width: upstream then puts the time/edited/read info on a
                                        // line of its own at the row's trailing edge, i.e. in the card's
                                        // bottom-right corner for every message. (With `false` it sits
                                        // right behind short text instead, so the corner is not consistent.)
                                        needsMaxWidth = true,
                                        isMentioned = isMentioned,
                                        showActionMenu = menuState::openActionMenu,
                                        content = { showMenu ->
                                            Box(Modifier.fillMaxWidth()) { content(showMenu) }
                                        },
                                    )
                                }
                            }
                        }
                        if (uiState.isUploading) {
                            TacitUploadProgress(
                                percent = uiState.uploadPercent,
                                label = uiState.uploadProgressLabel,
                            )
                        }
                    }
                }
            }

            if (!isPreview) {
                val reactionsSideModifier = Modifier
                    .zIndex(2f)
                    // + the card's own 8.dp inner padding, so the chips start on the message text's left edge
                    .padding(start = rowSidePadding + 8.dp + AvatarGutterWidth)
                    // The chips straddle the card's bottom edge: pulled up by half their height (plus the
                    // Column's 3.dp spacing) and drawn above the card (zIndex). The layout only reserves the
                    // half that hangs below the edge, so the next row is not pushed down further than needed.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val pullUp = placeable.height / 2 + 3.dp.roundToPx()
                        layout(placeable.width, placeable.height - pullUp) { placeable.place(0, -pullUp) }
                    }
                if (uiState.hasReactions) {
                    TacitMessageReactions(
                        modifier = reactionsSideModifier,
                        reactionEntries = uiState.reactionEntries,
                        isOwnMessage = isOwnMessage,
                        onToggleReaction = { reaction, reactedByMe ->
                            if (reactedByMe) timelineElementHolder?.removeReaction(reaction)
                            else timelineElementHolder?.addReaction(reaction)
                        },
                    )
                }
                if (uiState.showRedactionWarning) {
                    Box(Modifier.padding(start = rowSidePadding + 12.dp)) {
                        RedactionWarning(holder)
                    }
                }
            }
        }
    }
}

/** Feathered accent rings just outside the card; draw-only, so nothing in the layout moves. */
private fun DrawScope.drawMentionGlow(glowColor: Color) {
    val ringWidth = 2.dp.toPx()
    val cardRadius = TacitShapes.cardRadius.toPx()
    for (ring in 1..4) {
        val spread = ring * ringWidth - ringWidth / 2
        drawRoundRect(
            color = glowColor.copy(alpha = 0.16f / ring),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + 2 * spread, size.height + 2 * spread),
            cornerRadius = CornerRadius(cardRadius + spread),
            style = Stroke(width = ringWidth),
        )
    }
}

/**
 * Upload progress of a media message that is still being sent. [label] is produced (and localized)
 * by the view model; [percent] is `null` while the total size is unknown, in which case the bar is
 * indeterminate.
 */
@Composable
private fun TacitUploadProgress(percent: Float?, label: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val style = MaterialTheme.components.linearProgressIndicator
        if (percent != null) {
            ThemedProgressIndicator({ percent }, Modifier.weight(1f), style)
        } else {
            ThemedProgressIndicator(Modifier.weight(1f), style)
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.tacit.textMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * The hover affordance of a message row. Extracted into its own composable so that the action menu
 * entries, their five closures and the `canBeEdited`/`canBeRedacted`/`canBeReported` flow
 * subscriptions only exist while the row is actually hovered or one of its menus is open -- they
 * used to be rebuilt by every single pointer enter/exit of the parent.
 */
@Composable
private fun TacitFlatMessageHoverActions(
    holder: BaseTimelineElementHolderViewModel,
    uiState: TacitFlatMessageUiState,
    menuState: TacitMessageRowMenuState,
    rowSidePadding: Dp,
    isOwnMessage: Boolean,
    canReact: Boolean,
    additionalContextActions: @Composable ColumnScope.(onClose: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val i18n = DI.get<TacitI18nView>()
    val density = LocalDensity.current
    val timelineElementHolder = uiState.timelineElementHolder
    val outboxElementHolder = uiState.outboxElementHolder
    val canReply = timelineElementHolder?.canBeRepliedTo?.collectAsState()?.value == true
    val canEdit = timelineElementHolder?.canBeEdited?.collectAsState()?.value == true
    val canRedact = timelineElementHolder?.canBeRedacted?.collectAsState()?.value == true
    val canReport = timelineElementHolder?.canBeReported?.collectAsState()?.value == true

    // Copy, mirroring upstream's contextMenuActions: the element view knows how to turn its own
    // content into a clip entry, and a `null` entry means this element simply cannot be copied.
    val timelineElementViewSelector = DI.get<TimelineElementViewSelector>()
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val clipEntry = holder.element.collectAsState().value?.let { element ->
        timelineElementViewSelector.getClipEntry(holder, element)
    }

    val actions = tacitMessageActionMenuEntries(
        i18n = i18n,
        availability = TacitMessageActionAvailability(
            canReply = canReply,
            canEdit = canEdit,
            canRedact = canRedact,
            canReport = canReport,
            canCopy = clipEntry != null,
            canRetrySend = uiState.canRetrySend,
            canAbortSend = uiState.canAbortSend,
        ),
        callbacks = TacitMessageActionCallbacks(
            onCopy = {
                clipEntry?.let { entry -> clipboardScope.launch { clipboard.setClipEntry(entry) } }
                menuState.closeActionMenu()
            },
            onRetrySend = {
                outboxElementHolder?.retrySend()
                menuState.closeActionMenu()
            },
            onAbortSend = {
                outboxElementHolder?.abortSend()
                menuState.closeActionMenu()
            },
            onReply = {
                timelineElementHolder?.reply()
                menuState.closeActionMenu()
            },
            onEdit = {
                timelineElementHolder?.replace()
                menuState.closeActionMenu()
            },
            onShowInfo = {
                timelineElementHolder?.openTimelineElementMetadata()
                menuState.closeActionMenu()
            },
            onReport = {
                timelineElementHolder?.report()
                menuState.closeActionMenu()
            },
            onDelete = {
                timelineElementHolder?.redact()
                menuState.closeActionMenu()
            },
        ),
    )

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(
            x = with(density) { -(rowSidePadding + 2.dp).roundToPx() },
            y = with(density) { (-26).dp.roundToPx() },
        ),
    ) {
        Box(
            modifier = modifier
                .graphicsLayer { clip = false }
                .hoverable(menuState.quickActionsInteractionSource)
        ) {
            Row(
                modifier = Modifier
                    .clip(TacitShapes.control)
                    .background(MaterialTheme.tacit.surface, TacitShapes.control)
                    .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, TacitShapes.control)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canReact) {
                    TacitQuickActionButton(label = "🙂", description = i18n.tacitAddReactionAction()) {
                        menuState.openQuickReactions()
                    }
                }
                if (canReply) {
                    TacitQuickActionButton(label = "↩", description = i18n.tacitReplyAction()) {
                        timelineElementHolder.reply()
                        menuState.closeAllMenus()
                    }
                }
                TacitQuickActionButton(
                    label = i18n.tacitMoreActions(),
                    icon = Icons.Default.MoreVert
                ) { menuState.openActionMenu() }
            }

            TacitMessageActionDropdown(
                expanded = menuState.actionMenuOpen,
                onDismiss = menuState::closeActionMenu,
                actions = actions,
                additionalContextActions = additionalContextActions,
            )
            if (canReact) {
                TacitQuickReactionPickerMenu(
                    expanded = menuState.quickReactionsOpen,
                    alignEnd = isOwnMessage,
                    quickReactions = tacitQuickReactionShortcuts,
                    onDismiss = { menuState.quickReactionsOpen = false },
                    onSelectReaction = { reaction ->
                        timelineElementHolder?.addReaction(reaction)
                        menuState.quickReactionsOpen = false
                    },
                )
            }
        }
    }
}

/**
 * Runs [content] with upstream's message bubble styles coloured [color], the background the row actually
 * draws. Upstream only reads the colour as the contrast base of the sender name in MessageBubbleContent.
 */
@Composable
private fun WithMessageBubbleColor(color: Color, content: @Composable () -> Unit) {
    val base = MaterialTheme.components
    val styles = remember(base, color) {
        base.copy(
            messageBubbleOwn = base.messageBubbleOwn.copy(color = color),
            messageBubbleOther = base.messageBubbleOther.copy(color = color),
            messageBubbleError = base.messageBubbleError.copy(color = color),
        )
    }
    val components = remember(styles) {
        object : ThemeComponents {
            @Composable
            override fun create(): ComponentStyles = styles
        }
    }
    MaterialThemeComponents(components, content)
}
