package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.messenger.compose.view.pointerMoveFilter
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.viewmodel.room.list.RoomListMode
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListElementViewModel

@Composable
internal fun ChannelRow(
    roomListViewModel: RoomListViewModel,
    room: TacitRoomListElementViewModel,
    selectedRoomId: RoomId?,
    mode: RoomListMode,
    showInviteActions: Boolean = false,
    onAcceptInvite: (() -> Unit)? = null,
    onDeclineInvite: (() -> Unit)? = null,
) {
    val channelTitle = room.roomName.collectAsState().value ?: room.roomId.full
    val selected = selectedRoomId == room.roomId
    val dmMode = room.isDirectRoom.collectAsState().value
    val roomImageInitials = room.roomImageInitials.collectAsState().value
    val roomImage = room.roomImage.collectAsState().value
    val isUnread = room.isUnread.collectAsState().value == true
    val presence = room.presence.collectAsState().value
    val lastMessage = room.lastMessage.collectAsState().value
    val time = room.time.collectAsState().value
    val notificationCount = room.notificationCount.collectAsState().value
    var hovered by remember { mutableStateOf(false) }
    val inviteActionInProgress = if (showInviteActions) {
        room.rejectInvitationInProgress.collectAsState().value
    } else {
        false
    }

    Row(
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
                if (dmMode) {
                    TacitRoomNavigationState.showMembersPane = false
                }
                roomListViewModel.selectRoom(room.roomId)
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dmMode) {
            ThemedUserAvatar(
                initials = roomImageInitials ?: dmInitials(channelTitle),
                image = roomImage,
                size = 28.dp,
            )
            if (dmPresenceVisible(presence)) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dmPresenceColor(presence)),
                )
            } else {
                Spacer(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(8.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        } else {
            Text(
                text = "#",
                color = if (selected) Color.White else tacitLabelSubtle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(18.dp),
            )
        }

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
                if (dmMode) {
                    val presenceLabel = dmPresenceLabel(presence)
                    if (presenceLabel != null) {
                        Text(
                            text = presenceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) tacitTextOnSelected else tacitTextSubtle,
                        )
                    }
                }
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

        if (dmMode && !time.isNullOrBlank()) {
            Text(
                text = time,
                color = if (selected) tacitTextOnSelected else tacitLabelSubtle,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 6.dp),
                maxLines = 1,
            )
        }

        if (!notificationCount.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(tacitBadgeSuccessBackground)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
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
                    Text("Decline")
                }
                TextButton(
                    onClick = { onAcceptInvite?.invoke() },
                    enabled = !inviteActionInProgress,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Accept")
                }
            }
        }
    }
}

@Composable
internal fun DmOverview(
    rooms: List<TacitRoomListElementViewModel>,
) {
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
                title = "Chats",
                value = rooms.size.toString(),
                modifier = Modifier.weight(1f),
                valueColor = tacitText,
            )
            DmStatTile(
                title = "Online",
                value = online.toString(),
                modifier = Modifier.weight(1f),
                valueColor = accentColor,
            )
            DmStatTile(
                title = "Unread",
                value = unread.toString(),
                modifier = Modifier.weight(1f),
                valueColor = tacitAccentSoft,
            )
        }
        if (rooms.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rooms.take(6).forEach { room ->
                    val roomName = room.roomName.collectAsState().value
                    val roomImageInitials = room.roomImageInitials.collectAsState().value
                    val roomImage = room.roomImage.collectAsState().value
                    val presence = room.presence.collectAsState().value
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.BottomEnd) {
                        ThemedUserAvatar(
                            initials = roomImageInitials ?: dmInitials(roomName ?: room.roomId.full),
                            image = roomImage,
                            size = 26.dp,
                        )
                        if (dmPresenceVisible(presence)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dmPresenceColor(presence)),
                            )
                        }
                    }
                }
                if (rooms.size > 6) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(tacitCardBackgroundAlt)
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+${rooms.size - 6}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tacitTextMuted,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
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
