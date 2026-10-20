package org.fuchss.matrix.tacit.views.room.list

import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedButton
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.viewmodel.room.list.*
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

@Composable
internal fun RoomListBody(
    i18n: TacitI18nView,
    state: TacitRoomListUiState,
    actions: RoomListActions,
    modifier: Modifier = Modifier,
) {
    val mode = state.mode
    val rooms = state.rooms
    val visibleRooms = rooms.visibleRooms
    val inviteRooms = rooms.inviteRooms
    val guildChannelGroups = rooms.guildChannelGroups
    val selectedRoomId = state.status.selectedRoomId
    val searchResultsEmpty = state.status.searchResultsEmpty
    val selectedGuildInvite = state.selectedGuildInviteFallback
    val selectedGuildInviteVisible = selectedGuildInvite != null
    val hasAnyRooms = visibleRooms.isNotEmpty() || inviteRooms.isNotEmpty() || selectedGuildInviteVisible

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.tacit.surface)
    ) {
        when {
            rooms.allRoomsEmpty && mode.isDirectMessages() && state.status.canCreateNewRoomWithAccount && !searchResultsEmpty -> {
                EmptyRoomList(onCreateRoom = actions::createNewRoom)
            }

            !hasAnyRooms && mode is RoomListMode.GuildChannels -> {
                RoomListEmptyState(
                    title = i18n.tacitNoJoinedRoomsInGuildTitle(),
                    description = i18n.tacitNoJoinedRoomsInGuildDescription(),
                    actionLabel = i18n.tacitBrowseRoomsAction(),
                    onAction = actions::openBrowseChannels,
                )
            }

            searchResultsEmpty -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(i18n.roomListNoSearchResults(), color = MaterialTheme.tacit.text)
                }
            }

            !hasAnyRooms && mode.isDirectMessages() -> {
                RoomListEmptyState(
                    title = i18n.tacitNoDirectMessagesYet(),
                    description = i18n.tacitNoDirectMessagesDescription(),
                )
            }

            else -> {
                // The room list is composed from two different call sites in TacitMessengerView
                // (two pane vs. single pane). The view model keeps the scroll position (per account
                // and guild) and the DM filter, so a window resize across the breakpoint no longer
                // silently throws them away.
                val listState = rememberSavedLazyListState(state.scrollKey, actions)
                val dmFilter = state.dmFilter
                val filteredVisibleRooms = state.filteredRooms
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    if (visibleRooms.isNotEmpty() && mode.isDirectMessages()) {
                        DmOverview(
                            overview = state.dmOverview,
                            selectedFilter = dmFilter,
                            onFilterChange = actions::selectDmFilter,
                        )
                        Spacer(Modifier.height(8.dp))
                        RoomListSectionLabel(i18n.tacitRoomsSectionTitle(mode.isDirectMessages()))
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
                                    room = room,
                                    selectedRoomId = selectedRoomId,
                                    mode = mode,
                                    i18n = i18n,
                                    onSelectRoom = actions::selectRoom,
                                    showInviteActions = true,
                                    onAcceptInvite = {
                                        room.acceptInvitation()
                                        actions.selectRoom(room.roomId)
                                    },
                                    onDeclineInvite = {
                                        room.rejectInvitation()
                                    },
                                )
                            }
                        }

                        if (selectedGuildInvite != null) {
                            item {
                                SelectedGuildInviteRow(
                                    i18n = i18n,
                                    roomName = selectedGuildInvite.displayName ?: selectedGuildInvite.roomId.full,
                                    onAccept = actions::acceptSelectedGuildInvite,
                                    onDecline = actions::declineSelectedGuildInvite,
                                )
                            }
                        }

                        if (mode is RoomListMode.GuildChannels && filteredVisibleRooms.isNotEmpty()) {
                            item {
                                RoomListSectionLabel(i18n.tacitRoomsSectionTitle(mode.isDirectMessages()))
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
                                    room = room,
                                    selectedRoomId = selectedRoomId,
                                    mode = mode,
                                    i18n = i18n,
                                    onSelectRoom = actions::selectRoom,
                                )
                            }

                            guildChannelGroups.forEachIndexed { groupIndex, group ->
                                item(key = "category:${group.categoryRoomId.full}") {
                                    CategorySectionHeader(
                                        title = group.categoryName,
                                        canMoveUp = groupIndex > 0,
                                        canMoveDown = groupIndex < guildChannelGroups.lastIndex,
                                        moveUpDescription = i18n.tacitMoveCategoryUpDescription(),
                                        moveDownDescription = i18n.tacitMoveCategoryDownDescription(),
                                        settingsDescription = i18n.tacitOpenCategorySettingsDescription(),
                                        onMoveUp = { actions.reorderCategory(groupIndex, groupIndex - 1) },
                                        onMoveDown = { actions.reorderCategory(groupIndex, groupIndex + 1) },
                                        onOpenSettings = { actions.openSpaceSettings(group.categoryRoomId) },
                                    )
                                }
                                itemsIndexed(
                                    items = group.channels,
                                    key = { _, element -> "category:${group.categoryRoomId.full}:${element.roomId.full}" },
                                ) { _, room ->
                                    ChannelRow(
                                        room = room,
                                        selectedRoomId = selectedRoomId,
                                        mode = mode,
                                        i18n = i18n,
                                        onSelectRoom = actions::selectRoom,
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = filteredVisibleRooms,
                                key = { _, element -> element.roomId.full },
                            ) { _, room ->
                                ChannelRow(
                                    room = room,
                                    selectedRoomId = selectedRoomId,
                                    mode = mode,
                                    i18n = i18n,
                                    onSelectRoom = actions::selectRoom,
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

/** A list state restored from the view model, and saved back when it leaves the composition or [key] changes. */
@Composable
private fun rememberSavedLazyListState(key: String, actions: RoomListActions): LazyListState {
    val listState = remember(key) {
        val restored = actions.restoreScrollPosition(key)
        LazyListState(
            firstVisibleItemIndex = restored?.index ?: 0,
            firstVisibleItemScrollOffset = restored?.offset ?: 0,
        )
    }
    DisposableEffect(key, listState) {
        onDispose {
            actions.saveScrollPosition(key, TacitScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset))
        }
    }
    return listState
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
            tint = MaterialTheme.tacit.textMuted,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = emptyStateText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.tacit.textMuted,
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
            color = MaterialTheme.tacit.text,
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
                tint = MaterialTheme.tacit.textMuted,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                color = MaterialTheme.tacit.text,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                color = MaterialTheme.tacit.textMuted,
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
        color = MaterialTheme.tacit.textMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun CategorySectionHeader(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    moveUpDescription: String,
    moveDownDescription: String,
    settingsDescription: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val actionsVisibleAlpha = if (hovered) 1f else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .hoverable(hoverSource)
            .heightIn(min = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MaterialTheme.tacit.textMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Row(
            modifier = Modifier.alpha(actionsVisibleAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryHeaderActionButton(
                icon = Icons.Default.Settings,
                contentDescription = settingsDescription,
                onClick = onOpenSettings,
                enabled = hovered,
            )
            Spacer(Modifier.width(4.dp))
            CategoryHeaderActionButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = moveUpDescription,
                onClick = onMoveUp,
                enabled = hovered && canMoveUp,
            )
            Spacer(Modifier.width(4.dp))
            CategoryHeaderActionButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = moveDownDescription,
                onClick = onMoveDown,
                enabled = hovered && canMoveDown,
            )
        }
    }
}

@Composable
private fun CategoryHeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) MaterialTheme.tacit.surfaceAlt else MaterialTheme.tacit.surface)
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, RoundedCornerShape(6.dp))
            .alpha(if (enabled) 1f else TacitAlpha.disabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.tacit.textMuted,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun EmptyRoomList(
    onCreateRoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val i18n = DI.get<TacitI18nView>()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(i18n.roomListNoRoom())
            Spacer(Modifier.size(10.dp))
            ThemedButton(
                style = MaterialTheme.components.commonButton,
                onClick = onCreateRoom,
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
