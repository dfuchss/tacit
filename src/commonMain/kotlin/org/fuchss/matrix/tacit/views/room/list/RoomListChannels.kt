package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkAsUnread
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.pointerMoveFilter
import de.connect2x.trixnity.messenger.compose.view.theme.components.ButtonStyle
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedButton
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.viewmodel.room.list.RoomListMode
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListElementViewModel
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

@Composable
internal fun ChannelRow(
    roomListViewModel: RoomListViewModel,
    room: TacitRoomListElementViewModel,
    selectedRoomId: RoomId?,
    mode: RoomListMode,
    i18n: TacitI18nView,
    showInviteActions: Boolean = false,
    onAcceptInvite: (() -> Unit)? = null,
    onDeclineInvite: (() -> Unit)? = null,
) {
    val channelTitle = room.roomName.collectAsState().value ?: room.roomId.full
    val selected = selectedRoomId == room.roomId
    val isDirectRoom = room.isDirectRoom.collectAsState().value
    val roomImageInitials = room.roomImageInitials.collectAsState().value
    val roomImage = room.roomImage.collectAsState().value
    val isUnread = room.isUnread.collectAsState().value == true
    val presence = room.presence.collectAsState().value
    val lastMessage = room.lastMessage.collectAsState().value
    val time = room.time.collectAsState().value
    val notificationCount = room.notificationCount.collectAsState().value
    var hovered by remember { mutableStateOf(false) }
    // `hasFocus` covers the row itself and every focusable inside it (e.g. the read toggle), so the
    // toggle stays composed once the keyboard focus has moved into it.
    var rowFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val highlighted = hovered || rowFocused
    val showReadToggle = highlighted && !showInviteActions
    val itemSelectedBackground = tacitAccent(0.35f)
    val itemHoverBackground = tacitSurfaceAlt
    val itemUnreadBackground = tacitSurfaceAlt
    val badgeBackground = accentColor
    val badgeContent = tacitOnAccent(accentColor)
    val inviteActionInProgress = if (showInviteActions) {
        room.rejectInvitationInProgress.collectAsState().value
    } else {
        false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .clip(TacitShapes.card)
            .background(
                when {
                    selected -> itemSelectedBackground
                    highlighted -> itemHoverBackground
                    isUnread -> itemUnreadBackground
                    else -> Color.Transparent
                }
            )
            .border(
                1.dp,
                when {
                    selected -> accentColor.copy(alpha = 0.7f)
                    highlighted -> tacitBorder
                    else -> Color.Transparent
                },
                TacitShapes.card,
            )
            .pointerMoveFilter(
                onEnter = {
                    hovered = true
                    true
                },
                onExit = {
                    hovered = false
                    true
                },
            )
            .onFocusChanged { rowFocused = it.hasFocus }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
            ) {
                if (mode is RoomListMode.DirectMessages) {
                    TacitRoomNavigationState.showMembersPane = false
                }
                roomListViewModel.selectRoom(room.roomId)
            }
            .tacitInteractive(interactionSource = interactionSource, shape = TacitShapes.card)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val presenceLabel = if (isDirectRoom) dmPresenceLabel(presence, i18n) else null
                    if (presenceLabel != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(dmPresenceColor(presence).copy(alpha = 0.18f))
                                .border(
                                    width = 1.5.dp,
                                    color = dmPresenceColor(presence).copy(alpha = if (selected) 0.95f else 0.72f),
                                    shape = CircleShape,
                                )
                                // The ring is the only presence cue; without this it is colour-only
                                // and invisible to screen readers.
                                .semantics { contentDescription = presenceLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            ThemedUserAvatar(
                                initials = roomImageInitials ?: dmInitials(channelTitle),
                                image = roomImage,
                                size = 28.dp,
                            )
                        }
                    } else {
                        ThemedUserAvatar(
                            initials = roomImageInitials ?: dmInitials(channelTitle),
                            image = roomImage,
                            size = 28.dp,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))

                if (showInviteActions) {
                    // Invite rows squeeze two independently ellipsized lines into whatever the
                    // actions leave over ("Invitati…" / "from ta…"). Give the text the full row
                    // width (the actions move to their own line below) and offer the full text on
                    // hover for the rest.
                    val inviteTooltip = listOfNotNull(
                        channelTitle.takeIf { it.isNotBlank() },
                        lastMessage?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    Tooltip(
                        tooltip = { Text(inviteTooltip) },
                        modifier = Modifier.weight(1f),
                    ) {
                        ChannelRowText(
                            channelTitle = channelTitle,
                            lastMessage = lastMessage,
                            selected = selected,
                            isUnread = isUnread,
                            titleMaxLines = 2,
                        )
                    }
                } else {
                    ChannelRowText(
                        modifier = Modifier.weight(1f),
                        channelTitle = channelTitle,
                        lastMessage = lastMessage,
                        selected = selected,
                        isUnread = isUnread,
                        titleMaxLines = 1,
                    )
                }

                if (!showInviteActions) {
                    if (!time.isNullOrBlank()) {
                        Text(
                            text = time,
                            color = if (selected) tacitText else tacitTextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .alpha(if (showReadToggle) 0f else 1f),
                            maxLines = 1,
                        )
                    }

                    TrailingRoomIndicator(
                        showReadToggle = showReadToggle,
                        isUnread = isUnread,
                        notificationCount = notificationCount,
                        badgeBackground = badgeBackground,
                        badgeContent = badgeContent,
                        selected = selected,
                        onToggle = {
                            if (isUnread) room.markRead() else room.markUnread()
                        },
                        contentDescription = if (isUnread) i18n.markRoomAsRead() else i18n.markRoomAsUnread(),
                    )
                }
            }

            if (showInviteActions) {
                InviteActions(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    declineLabel = i18n.tacitDecline(),
                    acceptLabel = i18n.tacitAccept(),
                    onDecline = { onDeclineInvite?.invoke() },
                    onAccept = { onAcceptInvite?.invoke() },
                    enabled = !inviteActionInProgress,
                )
            }
        }
    }
}

@Composable
private fun ChannelRowText(
    channelTitle: String,
    lastMessage: String?,
    selected: Boolean,
    isUnread: Boolean,
    titleMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = channelTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Color.White else tacitText,
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isUnread && !selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (!lastMessage.isNullOrBlank()) {
            Text(
                text = lastMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) tacitText else tacitTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Accept/decline pair for invite rows.
 *
 * Styled once here instead of repeating raw Material `TextButton`s; also used for the
 * "selected guild" invite row in `RoomListBody`.
 */
@Composable
internal fun InviteActions(
    declineLabel: String,
    acceptLabel: String,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InviteActionButton(
            label = declineLabel,
            onClick = onDecline,
            enabled = enabled,
            primary = false,
        )
        InviteActionButton(
            label = acceptLabel,
            onClick = onAccept,
            enabled = enabled,
            primary = true,
        )
    }
}

@Composable
private fun InviteActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean,
) {
    val contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
    val focusedBorder = BorderStroke(2.dp, tacitText)
    val style = if (primary) {
        ButtonStyle.filled(
            shape = TacitShapes.compact,
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = tacitOnAccent(),
                disabledContainerColor = tacitSurfaceAlt,
                disabledContentColor = tacitTextMuted,
            ),
            elevation = null,
            contentPadding = contentPadding,
            textStyle = MaterialTheme.typography.labelMedium,
            focusedBorder = focusedBorder,
        )
    } else {
        ButtonStyle.outlined(
            shape = TacitShapes.compact,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = tacitText,
                disabledContentColor = tacitTextMuted,
            ),
            enabledBorder = BorderStroke(1.dp, tacitBorder),
            disabledBorder = BorderStroke(1.dp, tacitBorder),
            contentPadding = contentPadding,
            textStyle = MaterialTheme.typography.labelMedium,
            focusedBorder = focusedBorder,
        )
    }

    ThemedButton(
        onClick = onClick,
        enabled = enabled,
        style = style,
        modifier = Modifier.heightIn(min = 30.dp),
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun TrailingRoomIndicator(
    showReadToggle: Boolean,
    isUnread: Boolean,
    notificationCount: String?,
    badgeBackground: Color,
    badgeContent: Color,
    selected: Boolean,
    onToggle: () -> Unit,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .widthIn(min = 24.dp)
            .padding(start = 4.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (showReadToggle) {
            ReadToggleButton(
                isUnread = isUnread,
                onToggle = onToggle,
                contentDescription = contentDescription,
            )
        } else if (!notificationCount.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeBackground)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = notificationCount,
                    color = badgeContent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else if (isUnread) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) tacitText else Color.White)
            )
        } else {
            Spacer(Modifier.width(1.dp))
        }
    }
}

@Composable
private fun ReadToggleButton(
    isUnread: Boolean,
    onToggle: () -> Unit,
    contentDescription: String,
) {
    var hovered by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val background = if (hovered) tacitSurfaceAlt else tacitSurface
    val borderColor = tacitBorder
    val iconTint = if (isUnread) accentColor else tacitText
    val icon = if (isUnread) Icons.Default.MarkChatRead else Icons.Default.MarkAsUnread

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(TacitShapes.compact)
            .background(background)
            .border(1.dp, borderColor, TacitShapes.compact)
            .pointerMoveFilter(
                onEnter = {
                    hovered = true
                    true
                },
                onExit = {
                    hovered = false
                    true
                },
            )
            // Own focus target, so the toggle is reachable by keyboard instead of being buried in
            // the row-wide clickable.
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onToggle,
            )
            .tacitInteractive(interactionSource = interactionSource, shape = TacitShapes.compact),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun DmOverview(
    rooms: List<TacitRoomListElementViewModel>,
    selectedFilter: DmFilter,
    onFilterChange: (DmFilter) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    val unread = rooms.count { room -> room.isUnread.collectAsState().value == true }
    val online = rooms.count { room -> room.presence.collectAsState().value == Presence.ONLINE }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TacitShapes.card)
            .background(tacitSurfaceAlt)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DmStatTile(
                title = i18n.tacitDmStatChats(),
                value = rooms.size.toString(),
                modifier = Modifier.weight(1f),
                valueColor = tacitText,
                selected = selectedFilter == DmFilter.ALL,
                onClick = { onFilterChange(DmFilter.ALL) },
            )
            DmStatTile(
                title = i18n.tacitDmStatOnline(),
                value = online.toString(),
                modifier = Modifier.weight(1f),
                valueColor = accentColor,
                selected = selectedFilter == DmFilter.ONLINE,
                onClick = { onFilterChange(DmFilter.ONLINE) },
            )
            DmStatTile(
                title = i18n.tacitDmStatUnread(),
                value = unread.toString(),
                modifier = Modifier.weight(1f),
                valueColor = accentColor,
                selected = selectedFilter == DmFilter.UNREAD,
                onClick = { onFilterChange(DmFilter.UNREAD) },
            )
        }
    }
}

internal enum class DmFilter {
    ALL,
    ONLINE,
    UNREAD,
}

@Composable
private fun DmStatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(TacitShapes.control)
            .background(if (selected) tacitAccent(0.25f) else tacitSurface)
            .border(
                width = 1.dp,
                color = if (selected) accentColor else tacitBorder,
                shape = TacitShapes.control,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .tacitInteractive(interactionSource = interactionSource, shape = TacitShapes.control)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tacitTextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
