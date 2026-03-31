package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedButton
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import org.fuchss.matrix.tacit.tacitSurface
import org.fuchss.matrix.tacit.tacitText
import org.fuchss.matrix.tacit.tacitTextMuted
import org.fuchss.matrix.tacit.viewmodel.room.list.RoomListMode
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitGuildChannelGroup
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListElementViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.isDirectMessages
import org.fuchss.matrix.tacit.viewmodel.room.list.roomsSectionTitle
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

@Composable
internal fun RoomListBody(
    roomListViewModel: RoomListViewModel,
    i18n: TacitI18nView,
    mode: RoomListMode,
    selectedRoomId: RoomId?,
    visibleRooms: List<TacitRoomListElementViewModel>,
    guildChannelGroups: List<TacitGuildChannelGroup>,
    inviteRooms: List<TacitRoomListElementViewModel>,
    allRoomsEmpty: Boolean,
    canCreateNewRoomWithAccount: Boolean,
    searchResultsEmpty: Boolean,
    onBrowseRooms: () -> Unit,
    selectedGuildInviteRoomId: RoomId? = null,
    selectedGuildInviteName: String? = null,
    onAcceptSelectedGuildInvite: (() -> Unit)? = null,
    onDeclineSelectedGuildInvite: (() -> Unit)? = null,
) {
    val selectedGuildInviteVisible = selectedGuildInviteRoomId != null &&
            inviteRooms.none { it.roomId == selectedGuildInviteRoomId }
    val hasAnyRooms = visibleRooms.isNotEmpty() || inviteRooms.isNotEmpty() || selectedGuildInviteVisible

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tacitSurface)
    ) {
        when {
            allRoomsEmpty && mode.isDirectMessages() && canCreateNewRoomWithAccount && !searchResultsEmpty -> {
                EmptyRoomList(roomListViewModel)
            }

            !hasAnyRooms && mode is RoomListMode.GuildChannels -> {
                RoomListEmptyState(
                    title = i18n.tacitNoJoinedRoomsInGuildTitle(),
                    description = i18n.tacitNoJoinedRoomsInGuildDescription(),
                    actionLabel = i18n.tacitBrowseRoomsAction(),
                    onAction = onBrowseRooms,
                )
            }

            searchResultsEmpty -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(i18n.roomListNoSearchResults(), color = tacitText)
                }
            }

            !hasAnyRooms && mode.isDirectMessages() -> {
                RoomListEmptyState(
                    title = i18n.tacitNoDirectMessagesYet(),
                    description = i18n.tacitNoDirectMessagesDescription(),
                )
            }

            else -> {
                val listState = rememberLazyListState()
                var dmFilter by remember { mutableStateOf(DmFilter.ALL) }
                val filteredVisibleRooms = if (mode.isDirectMessages()) {
                    visibleRooms.filter { room ->
                        when (dmFilter) {
                            DmFilter.ALL -> true
                            DmFilter.UNREAD -> room.isUnread.collectAsState().value == true
                            DmFilter.ONLINE -> room.presence.collectAsState().value == Presence.ONLINE
                        }
                    }
                } else {
                    visibleRooms
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    if (visibleRooms.isNotEmpty() && mode.isDirectMessages()) {
                        DmOverview(
                            rooms = visibleRooms,
                            selectedFilter = dmFilter,
                            onFilterChange = { requested ->
                                dmFilter = if (requested == dmFilter && requested != DmFilter.ALL) {
                                    DmFilter.ALL
                                } else {
                                    requested
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        RoomListSectionLabel(mode.roomsSectionTitle())
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        state = listState,
                    ) {
                        if (filteredVisibleRooms.isEmpty() && inviteRooms.isEmpty() && !selectedGuildInviteVisible) {
                            item {
                                EmptyFilteredState(mode = mode, dmFilter = dmFilter, i18n = i18n)
                            }
                        }

                        if (inviteRooms.isNotEmpty() || selectedGuildInviteVisible) {
                            item {
                                RoomListSectionLabel(i18n.tacitInvitesSection())
                            }
                        }

                        if (inviteRooms.isNotEmpty()) {
                            itemsIndexed(
                                items = inviteRooms,
                                key = { _, element -> "invite:${element.roomId.full}" },
                            ) { _, room ->
                                ChannelRow(
                                    roomListViewModel = roomListViewModel,
                                    room = room,
                                    selectedRoomId = selectedRoomId,
                                    mode = mode,
                                    i18n = i18n,
                                    showInviteActions = true,
                                    onAcceptInvite = {
                                        room.acceptInvitation()
                                        roomListViewModel.selectRoom(room.roomId)
                                    },
                                    onDeclineInvite = {
                                        room.rejectInvitation()
                                    },
                                )
                            }
                        }

                        if (selectedGuildInviteVisible) {
                            item {
                                SelectedGuildInviteRow(
                                    i18n = i18n,
                                    roomName = selectedGuildInviteName ?: selectedGuildInviteRoomId.full,
                                    onAccept = onAcceptSelectedGuildInvite,
                                    onDecline = onDeclineSelectedGuildInvite,
                                )
                            }
                        }

                        if (mode is RoomListMode.GuildChannels && filteredVisibleRooms.isNotEmpty()) {
                            item {
                                RoomListSectionLabel(mode.roomsSectionTitle())
                            }
                        }

                        if (mode is RoomListMode.GuildChannels && guildChannelGroups.isNotEmpty()) {
                            val groupedRoomIds = guildChannelGroups
                                .flatMap { it.channels }
                                .map { it.roomId }
                                .toSet()
                            val ungroupedRooms = filteredVisibleRooms.filter { room ->
                                !groupedRoomIds.contains(room.roomId)
                            }

                            itemsIndexed(
                                items = ungroupedRooms,
                                key = { _, element -> element.roomId.full },
                            ) { _, room ->
                                ChannelRow(
                                    roomListViewModel = roomListViewModel,
                                    room = room,
                                    selectedRoomId = selectedRoomId,
                                    mode = mode,
                                    i18n = i18n,
                                )
                            }

                            guildChannelGroups.forEach { group ->
                                item(key = "category:${group.categoryRoomId.full}") {
                                    RoomListSectionLabel(group.categoryName)
                                }
                                itemsIndexed(
                                    items = group.channels,
                                    key = { _, element -> "category:${group.categoryRoomId.full}:${element.roomId.full}" },
                                ) { _, room ->
                                    ChannelRow(
                                        roomListViewModel = roomListViewModel,
                                        room = room,
                                        selectedRoomId = selectedRoomId,
                                        mode = mode,
                                        i18n = i18n,
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = filteredVisibleRooms,
                                key = { _, element -> element.roomId.full },
                            ) { _, room ->
                                ChannelRow(
                                    roomListViewModel = roomListViewModel,
                                    room = room,
                                    selectedRoomId = selectedRoomId,
                                    mode = mode,
                                    i18n = i18n,
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFilteredState(
    mode: RoomListMode,
    dmFilter: DmFilter,
    i18n: TacitI18nView,
) {
    val emptyStateIcon = when {
        mode.isDirectMessages() && dmFilter == DmFilter.ONLINE -> Icons.Default.Person
        mode.isDirectMessages() && dmFilter == DmFilter.UNREAD -> Icons.Default.MarkChatRead
        else -> Icons.AutoMirrored.Filled.Chat
    }
    val emptyStateText = when {
        mode.isDirectMessages() && dmFilter == DmFilter.ONLINE -> i18n.tacitNoOnlineDirectMessages()
        mode.isDirectMessages() && dmFilter == DmFilter.UNREAD -> i18n.tacitNoUnreadDirectMessages()
        else -> i18n.tacitNoDirectMessagesDescription()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = emptyStateIcon,
            contentDescription = null,
            tint = tacitTextMuted,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = emptyStateText,
            style = MaterialTheme.typography.bodySmall,
            color = tacitTextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SelectedGuildInviteRow(
    i18n: TacitI18nView,
    roomName: String,
    onAccept: (() -> Unit)?,
    onDecline: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = roomName,
            color = tacitText,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.material3.TextButton(onClick = { onDecline?.invoke() }) {
                Text(i18n.tacitDecline())
            }
            androidx.compose.material3.TextButton(onClick = { onAccept?.invoke() }) {
                Text(i18n.tacitAccept())
            }
        }
    }
}

@Composable
private fun RoomListEmptyState(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                )
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = tacitTextMuted,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                color = tacitText,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                color = tacitTextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                ThemedButton(
                    style = MaterialTheme.components.commonButton,
                    onClick = onAction,
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun RoomListSectionLabel(text: String) {
    Text(
        text = text,
        color = tacitTextMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
internal fun EmptyRoomList(roomListViewModel: RoomListViewModel) {
    val i18n = DI.get<TacitI18nView>()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(i18n.roomListNoRoom())
            Spacer(Modifier.size(10.dp))
            ThemedButton(
                style = MaterialTheme.components.commonButton,
                onClick = { roomListViewModel.createNewRoom() },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    i18n.accountCreateNewRoom(),
                    modifier = Modifier.size(MaterialTheme.components.primaryButton.iconSize),
                )
                Spacer(Modifier.size(MaterialTheme.components.primaryButton.iconSpacing))
                Text(i18n.roomListCreateRoom())
            }
        }
    }
}
