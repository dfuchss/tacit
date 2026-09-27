package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.pointerMoveFilter
import de.connect2x.trixnity.messenger.compose.view.room.timeline.RedactionWarning
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementViewSelector
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.MessageBubbleContent
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.MessageBubbleView
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

/** Width of the avatar gutter: avatar (30.dp) + the spacer that separates it from the text column. */
private val AvatarGutterWidth = 40.dp

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
    additionalContextActions: @Composable ColumnScope.(onClose: () -> Unit) -> Unit,
    isPreview: Boolean,
    isMentioned: Boolean,
    index: Int,
    content: @Composable (showActionMenu: () -> Unit) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    val timelineElementViewSelector = DI.get<TimelineElementViewSelector>()
    val element = holder.element.collectAsState().value
    val sender = holder.sender.collectAsState().value

    val hoverMessage = remember { mutableStateOf(false) }
    val hoverQuickActions = remember { mutableStateOf(false) }
    val quickReactionsOpen = remember { mutableStateOf(false) }
    val allReactionsOpen = remember { mutableStateOf(false) }
    val showActionMenu = remember { mutableStateOf(false) }
    // The row is "active" while it is hovered or while one of its menus is open. Everything that
    // used to be painted permanently (card background, border, quick actions) is bound to this.
    val isRowActive = hoverMessage.value ||
            hoverQuickActions.value ||
            showActionMenu.value ||
            quickReactionsOpen.value ||
            allReactionsOpen.value
    val hoverInteractionSource = remember { MutableInteractionSource() }

    val timelineElementHolder = uiState.timelineElementHolder
    val isOwnMessage = holder.isByMe
    // Previews (e.g. the quoted element of a reply) are rendered out of timeline context, so the
    // grouping signal of the original position must not strip their avatar.
    val isContinuation = uiState.isContinuation && !isPreview
    val canReact = timelineElementHolder?.canBeReactedTo?.collectAsState()?.value == true

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .zIndex(if (isRowActive) 100f else 0f)
            .graphicsLayer { clip = false }
    ) {
        val rowSidePadding = if (maxWidth < 400.dp) 10.dp else 20.dp
        val density = LocalDensity.current
        // The card treatment is a hover/active affordance instead of a permanent per-message frame.
        // `Modifier.background`/`Modifier.border` are draw-only, so painting them transparent keeps
        // the layout byte-for-byte identical and the row cannot jump when the pointer enters.
        val rowHighlightColor = when {
            !isRowActive -> Color.Transparent
            isOwnMessage -> tacitSurfaceAlt.copy(alpha = 0.88f)
            else -> tacitSurfaceAlt.copy(alpha = 0.78f)
        }
        val rowBorderColor = when {
            !isRowActive -> Color.Transparent
            isOwnMessage -> accentColor.copy(alpha = 0.56f)
            else -> tacitBorder.copy(alpha = 0.9f)
        }
        // Consecutive messages of the same sender are pulled together and lose the card's inner
        // breathing room; a sender change or a long pause (showBigGap) opens the group up again.
        val outerTopPadding = when {
            isContinuation -> 2.dp
            uiState.showBigGap -> 14.dp
            else -> 6.dp
        }
        val rowVerticalPadding: Dp = if (isContinuation) 2.dp else 6.dp

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
                    .clip(TacitShapes.card)
                    .background(rowHighlightColor, TacitShapes.card)
                    .border(1.dp, rowBorderColor, TacitShapes.card)
                    .hoverable(hoverInteractionSource)
                    .pointerMoveFilter(
                        onEnter = {
                            hoverMessage.value = true
                            true
                        },
                        onExit = {
                            hoverMessage.value = false
                            true
                        },
                    )
                    .pointerInput(holder) {
                        detectTapGestures(onLongPress = { showActionMenu.value = true })
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
                                showActionMenu.value = true
                            }
                        }
                    }
                    .semantics {
                        collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                        this.text = AnnotatedString(
                            "${sender?.name ?: i18n.commonUnknown()} (${holder.formattedTime}): " +
                                    (element?.let { timelineElementViewSelector.a11yLabel(it, i18n) } ?: "")
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = rowVerticalPadding)
            ) {
                if (!isPreview && isRowActive) {
                    TacitFlatMessageHoverActions(
                        timelineElementHolder = timelineElementHolder,
                        i18n = i18n,
                        rowSidePadding = rowSidePadding,
                        density = density,
                        isOwnMessage = isOwnMessage,
                        canReact = canReact,
                        hoverQuickActions = hoverQuickActions,
                        quickReactionsOpen = quickReactionsOpen,
                        allReactionsOpen = allReactionsOpen,
                        showActionMenu = showActionMenu,
                        additionalContextActions = additionalContextActions,
                    )
                }
                if (canReact) {
                    TacitReactionPickerMenu(
                        expanded = allReactionsOpen.value,
                        alignEnd = true,
                        yOffset = (-16).dp,
                        onDismiss = { allReactionsOpen.value = false },
                        onSelectReaction = { reaction ->
                            timelineElementHolder.addReaction(reaction)
                            allReactionsOpen.value = false
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
                        ThemedUserAvatar(
                            initials = uiState.senderInitials ?: "?",
                            image = uiState.senderAvatar,
                            size = 30.dp,
                            // deliberate optical offset against the first text line, not a bug
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    if (uiState.redactionInProgress) {
                        Icon(
                            imageVector = Icons.Default.AutoDelete,
                            contentDescription = i18n.messageBubbleBeingDeleted(),
                            modifier = Modifier
                                .size(16.dp)
                                .padding(2.dp),
                        )
                    }
                    Box(Modifier.fillMaxWidth()) {
                        val baseTypography = MaterialTheme.typography
                        val mutedColor = tacitTextMuted
                        val timelineTypography = remember(baseTypography, mutedColor) {
                            baseTypography.copy(
                                labelSmall = baseTypography.labelSmall.copy(color = mutedColor),
                            )
                        }
                        MaterialTheme(
                            colorScheme = MaterialTheme.colorScheme,
                            typography = timelineTypography,
                            shapes = MaterialTheme.shapes,
                        ) {
                            MessageBubbleContent(
                                holder = holder,
                                needsMaxWidth = true,
                                isMentioned = isMentioned,
                                showActionMenu = { showActionMenu.value = true },
                                content = { showMenu ->
                                    Box(Modifier.fillMaxWidth()) { content(showMenu) }
                                },
                            )
                        }
                    }
                }
            }

            if (!isPreview) {
                val reactionsSideModifier = Modifier
                    .zIndex(2f)
                    // The pull-up has to shrink with the card's bottom padding, otherwise the
                    // reactions would creep into a compact row by the 4.dp it saves.
                    .padding(start = rowSidePadding + AvatarGutterWidth)
                    .offset(y = -(14.dp + rowVerticalPadding))
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

/**
 * The hover affordance of a message row. Extracted into its own composable so that the action menu
 * entries, their five closures and the `canBeEdited`/`canBeRedacted`/`canBeReported` flow
 * subscriptions only exist while the row is actually hovered or one of its menus is open -- they
 * used to be rebuilt by every single pointer enter/exit of the parent.
 */
@Composable
private fun TacitFlatMessageHoverActions(
    timelineElementHolder: TimelineElementHolderViewModel?,
    i18n: TacitI18nView,
    rowSidePadding: Dp,
    density: Density,
    isOwnMessage: Boolean,
    canReact: Boolean,
    hoverQuickActions: MutableState<Boolean>,
    quickReactionsOpen: MutableState<Boolean>,
    allReactionsOpen: MutableState<Boolean>,
    showActionMenu: MutableState<Boolean>,
    additionalContextActions: @Composable ColumnScope.(onClose: () -> Unit) -> Unit,
) {
    val canReply = timelineElementHolder?.canBeRepliedTo?.collectAsState()?.value == true
    val canEdit = timelineElementHolder?.canBeEdited?.collectAsState()?.value == true
    val canRedact = timelineElementHolder?.canBeRedacted?.collectAsState()?.value == true
    val canReport = timelineElementHolder?.canBeReported?.collectAsState()?.value == true
    val actions = tacitMessageActionMenuEntries(
        i18n = i18n,
        canReply = canReply,
        canEdit = canEdit,
        canRedact = canRedact,
        canReport = canReport,
        onReply = {
            timelineElementHolder?.reply()
            showActionMenu.value = false
        },
        onEdit = {
            timelineElementHolder?.replace()
            showActionMenu.value = false
        },
        onShowInfo = {
            timelineElementHolder?.openTimelineElementMetadata()
            showActionMenu.value = false
        },
        onReport = {
            timelineElementHolder?.report()
            showActionMenu.value = false
        },
        onDelete = {
            timelineElementHolder?.redact()
            showActionMenu.value = false
        },
    )

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(
            x = with(density) { -(rowSidePadding + 2.dp).roundToPx() },
            y = with(density) { (-26).dp.roundToPx() },
        ),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { clip = false }
                .pointerMoveFilter(
                    onEnter = {
                        hoverQuickActions.value = true
                        true
                    },
                    onExit = {
                        hoverQuickActions.value = false
                        true
                    },
                )
        ) {
            Row(
                modifier = Modifier
                    .clip(TacitShapes.control)
                    .background(tacitSurface, TacitShapes.control)
                    .border(1.dp, tacitBorder, TacitShapes.control)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canReact) {
                    TacitQuickActionButton(label = "🙂") {
                        quickReactionsOpen.value = true
                        allReactionsOpen.value = false
                    }
                }
                if (canReply) {
                    TacitQuickActionButton(label = "↩") {
                        timelineElementHolder.reply()
                        quickReactionsOpen.value = false
                        allReactionsOpen.value = false
                        showActionMenu.value = false
                    }
                }
                TacitQuickActionButton(
                    label = "More",
                    icon = Icons.Default.MoreVert
                ) { showActionMenu.value = true }
            }

            TacitMessageActionDropdown(
                expanded = showActionMenu.value,
                onDismiss = { showActionMenu.value = false },
                actions = actions,
                additionalContextActions = additionalContextActions,
            )
            if (canReact) {
                TacitQuickReactionPickerMenu(
                    expanded = quickReactionsOpen.value,
                    alignEnd = isOwnMessage,
                    quickReactions = tacitQuickReactionShortcuts,
                    onDismiss = { quickReactionsOpen.value = false },
                    onSelectReaction = { reaction ->
                        timelineElementHolder?.addReaction(reaction)
                        quickReactionsOpen.value = false
                    },
                )
            }
        }
    }
}
