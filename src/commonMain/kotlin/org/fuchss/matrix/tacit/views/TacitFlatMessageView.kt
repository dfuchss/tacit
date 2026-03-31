package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
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
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

class TacitFlatMessageView : MessageBubbleView {
    @Composable
    override fun create(
        holder: BaseTimelineElementHolderViewModel,
        needsMaxWidth: Boolean,
        additionalContextActions: @Composable ColumnScope.(onClose: () -> Unit) -> Unit,
        isPreview: Boolean,
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
    val showInlineTime = hoverMessage.value ||
            hoverQuickActions.value ||
            showActionMenu.value ||
            quickReactionsOpen.value ||
            allReactionsOpen.value
    val hoverInteractionSource = remember { MutableInteractionSource() }

    val timelineElementHolder = uiState.timelineElementHolder
    val isOwnMessage = holder.isByMe
    val canReact = timelineElementHolder?.canBeReactedTo?.collectAsState()?.value == true
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

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .zIndex(if (showInlineTime) 100f else 0f)
            .graphicsLayer { clip = false }
    ) {
        val rowSidePadding = if (maxWidth < 400.dp) 10.dp else 20.dp
        val incomingTextColumnOffset = 40.dp
        val density = LocalDensity.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (uiState.showBigGap) 12.dp else 5.dp)
                .zIndex(if (showInlineTime) 10f else 0f)
                .graphicsLayer { clip = false },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = rowSidePadding)
                    .fillMaxWidth()
                    .graphicsLayer { clip = false }
                    .background(
                        when {
                            hoverMessage.value -> tacitSurfaceAlt.copy(alpha = 0.45f)
                            isOwnMessage -> tacitAccent(0.18f)
                            else -> Color.Transparent
                        }
                    )
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
                    .semantics {
                        collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                        this.text = AnnotatedString(
                            "${sender?.name ?: i18n.commonUnknown()} (${holder.formattedTime}): " +
                                    (element?.let { timelineElementViewSelector.a11yLabel(it, i18n) } ?: "")
                        )
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (!isPreview && showInlineTime) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(
                            x = with(density) { -(rowSidePadding + 6.dp).roundToPx() },
                            y = with(density) { (-16).dp.roundToPx() },
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tacitSurface, RoundedCornerShape(14.dp))
                                    .border(1.dp, tacitBorder, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 5.dp, vertical = 4.dp),
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
                                        timelineElementHolder.addReaction(reaction)
                                        quickReactionsOpen.value = false
                                    },
                                )
                            }
                        }
                    }
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
                    ThemedUserAvatar(
                        initials = uiState.senderInitials ?: "?",
                        image = uiState.senderAvatar,
                        size = 28.dp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.width(8.dp))
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
                        val timelineTypography = baseTypography.copy(
                            labelSmall = baseTypography.labelSmall.copy(color = Color.Transparent),
                        )
                        MaterialTheme(
                            colorScheme = MaterialTheme.colorScheme,
                            typography = timelineTypography,
                            shapes = MaterialTheme.shapes,
                        ) {
                            MessageBubbleContent(
                                holder = holder,
                                needsMaxWidth = needsMaxWidth,
                                showActionMenu = { showActionMenu.value = true },
                                content = content,
                            )
                        }
                    }
                }

                if (!isPreview && showInlineTime) {
                    Text(
                        text = holder.formattedTime,
                        color = tacitTextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 2.dp),
                    )
                }
            }

            if (!isPreview) {
                val reactionsSideModifier = Modifier
                    .padding(start = rowSidePadding + incomingTextColumnOffset)
                    .offset(y = (-5).dp)
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
