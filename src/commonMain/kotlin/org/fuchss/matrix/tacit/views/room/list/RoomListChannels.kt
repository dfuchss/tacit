package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val inviteActionInProgress = if (showInviteActions) {
        room.rejectInvitationInProgress.collectAsState().value
    } else {
        false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> tacitItemSelectedBackground
                    hovered -> tacitItemHoverBackground
                    isUnread -> tacitItemUnreadBackground
                    else -> Color.Transparent
                }
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
                if (isDirectRoom && dmPresenceVisible(presence)) {
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
                        color = if (selected) tacitTextOnSelected else tacitTextSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!time.isNullOrBlank()) {
                Text(
                    text = time,
                    color = if (selected) tacitTextOnSelected else tacitLabelSubtle,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .alpha(if (showReadToggle) 0f else 1f),
                    maxLines = 1,
                )
            }

            if (!notificationCount.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(tacitBadgeSuccessBackground)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .alpha(if (showReadToggle) 0f else 1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = notificationCount,
                        color = tacitBadgeSuccessContent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (isUnread && !selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
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

        if (showReadToggle) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .zIndex(1f),
                contentAlignment = Alignment.Center,
            ) {
                ReadToggleButton(
                    isUnread = isUnread,
                    onToggle = {
                        if (isUnread) room.markRead() else room.markUnread()
                    },
                    contentDescription = if (isUnread) i18n.markRoomAsRead() else i18n.markRoomAsUnread(),
                )
            }
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
    val background = if (hovered) tacitActionHoverBackground else tacitActionBackground
    val borderColor = tacitActionBorder
    val iconTint = if (isUnread) tacitActionPrimaryIconTint else tacitActionIconTint
    val icon = if (isUnread) Icons.Default.MarkChatRead else Icons.Default.MarkAsUnread

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
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
) {
    val i18n = DI.get<TacitI18nView>()
    val unread = rooms.count { room -> room.isUnread.collectAsState().value == true }
    val online = rooms.count { room -> room.presence.collectAsState().value == Presence.ONLINE }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tacitCardBackground)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DmStatTile(
                title = i18n.tacitDmStatChats(),
                value = rooms.size.toString(),
                modifier = Modifier.weight(1f),
                valueColor = tacitText,
            )
            DmStatTile(
                title = i18n.tacitDmStatOnline(),
                value = online.toString(),
                modifier = Modifier.weight(1f),
                valueColor = accentColor,
            )
            DmStatTile(
                title = i18n.tacitDmStatUnread(),
                value = unread.toString(),
                modifier = Modifier.weight(1f),
                valueColor = tacitAccentSoft,
            )
        }
    }
}

@Composable
private fun DmStatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tacitCardBackgroundAlt)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tacitTextSubtle,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
