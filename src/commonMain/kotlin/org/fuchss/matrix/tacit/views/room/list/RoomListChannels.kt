package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkAsUnread
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.pointerMoveFilter
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
    val showReadToggle = hovered && !showInviteActions
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
                    hovered -> itemHoverBackground
                    isUnread -> itemUnreadBackground
                    else -> Color.Transparent
                }
            )
            .border(
                1.dp,
                when {
                    selected -> accentColor.copy(alpha = 0.7f)
                    hovered -> tacitBorder
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
            .clickable {
                if (mode is RoomListMode.DirectMessages) {
                    TacitRoomNavigationState.showMembersPane = false
                }
                roomListViewModel.selectRoom(room.roomId)
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (isDirectRoom && presence == Presence.ONLINE) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(dmPresenceColor(presence).copy(alpha = 0.18f))
                            .border(
                                width = 1.5.dp,
                                color = dmPresenceColor(presence).copy(alpha = if (selected) 0.95f else 0.72f),
                                shape = CircleShape,
                            ),
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

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channelTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) Color.White else tacitText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isUnread && !selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
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

            if (!showInviteActions) {
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

            if (showInviteActions) {
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = { onDeclineInvite?.invoke() },
                        enabled = !inviteActionInProgress,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text(i18n.tacitDecline())
                    }
                    TextButton(
                        onClick = { onAcceptInvite?.invoke() },
                        enabled = !inviteActionInProgress,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text(i18n.tacitAccept())
                    }
                }
            }
        }

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
            .clickable(onClick = onToggle),
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
    Column(
        modifier = modifier
            .clip(TacitShapes.control)
            .background(if (selected) tacitAccent(0.25f) else tacitSurface)
            .border(
                width = 1.dp,
                color = if (selected) accentColor else tacitBorder,
                shape = TacitShapes.control,
            )
            .clickable(onClick = onClick)
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
