package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.roomlist.RoomListView
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel.UserSyncStates
import de.connect2x.trixnity.messenger.viewmodel.util.ErrorType
import kotlinx.coroutines.delay
import org.fuchss.matrix.tacit.TacitRoomNavigationState
import org.fuchss.matrix.tacit.tacitBorder
import org.fuchss.matrix.tacit.tacitSurface
import org.fuchss.matrix.tacit.tacitBackground
import org.fuchss.matrix.tacit.tacitWarningBg
import org.fuchss.matrix.tacit.tacitWarningBorder
import org.fuchss.matrix.tacit.tacitWarningText
import org.fuchss.matrix.tacit.viewmodel.room.list.RoomListMode
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListElementViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.selectedGuildOrNull
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.list.dialogs.browse.BrowseChannelsDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.create.CreateChannelDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.create.CreateGroupChannelDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.create.CreateGuildDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.direct.CreateDirectMessageDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.invite.InviteToGuildDialogContainer

private const val ERROR_AUTO_DISMISS_MS = 8_000L

class TacitRoomListView : RoomListView {
    @Composable
    override fun create(roomListViewModel: RoomListViewModel) {
        val tacitRoomListViewModel = roomListViewModel as? TacitRoomListViewModel
            ?: error("TacitRoomListView requires RoomListViewModelFactory to provide TacitRoomListViewModel.")
        val i18n = DI.get<TacitI18nView>()
        LaunchedEffect(Unit) {
            roomListViewModel.showSearch.value = false
        }

        val tacitRooms = tacitRoomListViewModel.typedElements.collectAsState().value
        val guilds = tacitRoomListViewModel.guilds.collectAsState().value
        val guildAvatars = tacitRoomListViewModel.guildAvatars.collectAsState().value
        val mode = tacitRoomListViewModel.mode.collectAsState().value
        val visibleRooms = tacitRoomListViewModel.visibleRooms.collectAsState().value
        val inviteRooms = tacitRoomListViewModel.inviteRooms.collectAsState().value
        val dmUnreadCount = tacitRoomListViewModel.dmUnreadCount.collectAsState().value
        val guildUnreadCounts = tacitRoomListViewModel.guildUnreadCounts.collectAsState().value
        val browseChannels = tacitRoomListViewModel.browseChannels.collectAsState().value
        val selectedGuild = tacitRoomListViewModel.selectedGuild.collectAsState().value
        val selectedGuildInviteFallback =
            selectedGuild?.takeIf { guild -> guild.isInvite && inviteRooms.none { it.roomId == guild.roomId } }
        val selectedRoomId = roomListViewModel.selectedRoomId.collectAsState().value
        val pendingRoomIdToOpen = TacitRoomNavigationState.openRoomForRoomId
        val canCreateNewRoomWithAccount = roomListViewModel.canCreateNewRoomWithAccount.collectAsState().value
        val searchResultsEmpty = roomListViewModel.searchResultsEmpty.collectAsState().value
        val error = tacitRoomListViewModel.error.collectAsState().value
        val errorType = tacitRoomListViewModel.errorType.collectAsState().value
        val canUseSelectedGuildAccount = tacitRoomListViewModel.canUseSelectedGuildAccount.collectAsState().value
        val canUsePreferredCreationAccount =
            tacitRoomListViewModel.canUsePreferredCreationAccount.collectAsState().value
        val createGuildInProgress = tacitRoomListViewModel.createGuildInProgress.collectAsState().value
        val createChannelInProgress = tacitRoomListViewModel.createChannelInProgress.collectAsState().value
        val joiningChannelRoomId = tacitRoomListViewModel.joiningChannelRoomId.collectAsState().value
        val inviteToGuildInProgress = tacitRoomListViewModel.inviteToGuildInProgress.collectAsState().value
        val createDirectMessageInProgress = tacitRoomListViewModel.createDirectMessageInProgress.collectAsState().value
        val createGroupChannelInProgress = tacitRoomListViewModel.createGroupChannelInProgress.collectAsState().value
        val guildInviteActionInProgress = tacitRoomListViewModel.guildInviteActionInProgress.collectAsState().value
        val syncStates = roomListViewModel.syncStates.collectAsState().value

        LaunchedEffect(pendingRoomIdToOpen, tacitRooms) {
            val pendingRoomId = pendingRoomIdToOpen ?: return@LaunchedEffect
            val targetRoom = tacitRooms.firstOrNull { it.roomId.full == pendingRoomId } ?: return@LaunchedEffect
            roomListViewModel.selectRoom(targetRoom.roomId)
            TacitRoomNavigationState.openRoomForRoomId = null
            TacitRoomNavigationState.showMembersPane = false
        }

        var createGuildDialogOpen by remember { mutableStateOf(false) }
        var createChannelDialogOpen by remember { mutableStateOf(false) }
        var browseChannelsDialogOpen by remember { mutableStateOf(false) }
        var inviteToGuildDialogOpen by remember { mutableStateOf(false) }
        var createDirectMessageDialogOpen by remember { mutableStateOf(false) }
        var createGroupChannelDialogOpen by remember { mutableStateOf(false) }

        AutoDismissError(error, errorType) {
            roomListViewModel.errorDismiss()
        }

        LaunchedEffect(selectedGuild, guilds) {
            if (selectedGuild == null) return@LaunchedEffect
            val selectedGuildStillVisible = guilds.any { guild ->
                guild.roomId == selectedGuild.roomId && guild.userId == selectedGuild.userId
            }
            if (!selectedGuildStillVisible) {
                tacitRoomListViewModel.selectGuild(null)
            }
        }

        RoomListContent(
            roomListViewModel = roomListViewModel,
            i18n = i18n,
            guilds = guilds,
            guildAvatars = guildAvatars,
            mode = mode,
            selectedRoomId = selectedRoomId,
            visibleRooms = visibleRooms,
            inviteRooms = inviteRooms,
            allRoomsEmpty = tacitRooms.isEmpty(),
            canCreateNewRoomWithAccount = canCreateNewRoomWithAccount,
            searchResultsEmpty = searchResultsEmpty,
            dmUnreadCount = dmUnreadCount,
            guildUnreadCounts = guildUnreadCounts,
            selectedGuildAccountAvailable = canUseSelectedGuildAccount,
            error = error,
            errorType = errorType,
            onDismissError = { roomListViewModel.errorDismiss() },
            syncStates = syncStates,
            selectedGuildInviteFallback = selectedGuildInviteFallback,
            onAcceptSelectedGuildInvite = {
                val targetGuild = selectedGuildInviteFallback
                if (targetGuild == null) {
                    tacitRoomListViewModel.reportError(i18n.tacitNoAccountForGuildInviteError())
                    return@RoomListContent
                }
                if (guildInviteActionInProgress) return@RoomListContent
                tacitRoomListViewModel.acceptGuildInvite(targetGuild)
            },
            onDeclineSelectedGuildInvite = {
                val targetGuild = selectedGuildInviteFallback
                if (targetGuild == null) {
                    tacitRoomListViewModel.reportError(i18n.tacitNoAccountForGuildInviteError())
                    return@RoomListContent
                }
                if (guildInviteActionInProgress) return@RoomListContent
                tacitRoomListViewModel.declineGuildInvite(targetGuild)
            },
            onSelectGuild = { tacitRoomListViewModel.selectGuild(it) },
            onReorderGuild = tacitRoomListViewModel::reorderGuild,
            onOpenCreateGuild = {
                roomListViewModel.errorDismiss()
                createGuildDialogOpen = true
            },
            onCreateRoom = {
                roomListViewModel.errorDismiss()
                when (mode) {
                    is RoomListMode.DirectMessages -> {
                        createDirectMessageDialogOpen = true
                    }

                    is RoomListMode.GuildChannels -> createChannelDialogOpen = true
                }
            },
            onCreateGroupChannel = {
                roomListViewModel.errorDismiss()
                createGroupChannelDialogOpen = true
            },
            onOpenBrowseChannels = {
                roomListViewModel.errorDismiss()
                browseChannelsDialogOpen = true
            },
            onOpenInviteToGuild = {
                roomListViewModel.errorDismiss()
                inviteToGuildDialogOpen = true
            },
            onOpenGuildSettings = selectedGuild?.let { guild ->
                {
                    TacitRoomNavigationState.openSettingsForRoomId = guild.roomId.full
                    TacitRoomNavigationState.suppressBackButtonForRoomId = guild.roomId.full
                    roomListViewModel.selectRoom(guild.roomId)
                }
            },
        )

        CreateGuildDialogContainer(
            open = createGuildDialogOpen,
            inProgress = createGuildInProgress,
            canCreateGuild = canUsePreferredCreationAccount,
            onCreateGuild = tacitRoomListViewModel::createGuild,
            onSetOpen = { createGuildDialogOpen = it },
        )

        CreateChannelDialogContainer(
            open = createChannelDialogOpen,
            inProgress = createChannelInProgress,
            selectedGuild = selectedGuild,
            canCreateChannel = canUseSelectedGuildAccount,
            onCreateChannel = tacitRoomListViewModel::createChannel,
            onSetOpen = { createChannelDialogOpen = it },
        )

        BrowseChannelsDialogContainer(
            open = browseChannelsDialogOpen,
            joiningRoomId = joiningChannelRoomId,
            selectedGuild = selectedGuild,
            browseChannels = browseChannels,
            canJoinChannels = canUseSelectedGuildAccount,
            onJoinChannel = tacitRoomListViewModel::joinChannel,
            onSetOpen = { browseChannelsDialogOpen = it },
        )

        InviteToGuildDialogContainer(
            open = inviteToGuildDialogOpen,
            inProgress = inviteToGuildInProgress,
            selectedGuild = selectedGuild,
            canInviteMember = canUseSelectedGuildAccount,
            canSearchUsers = canUseSelectedGuildAccount,
            searchUsers = tacitRoomListViewModel::searchUsersForGuild,
            onInviteMember = tacitRoomListViewModel::inviteUserToGuild,
            onSetOpen = { inviteToGuildDialogOpen = it },
        )

        CreateDirectMessageDialogContainer(
            open = createDirectMessageDialogOpen,
            inProgress = createDirectMessageInProgress,
            canStartDirectMessage = canUsePreferredCreationAccount,
            canSearchUsers = canUsePreferredCreationAccount,
            searchUsers = tacitRoomListViewModel::searchUsersForDirectMessages,
            onStartDirectMessage = tacitRoomListViewModel::startDirectMessage,
            onSetOpen = { createDirectMessageDialogOpen = it },
        )

        CreateGroupChannelDialogContainer(
            open = createGroupChannelDialogOpen,
            inProgress = createGroupChannelInProgress,
            canCreateGroupChannel = canUsePreferredCreationAccount,
            onCreateGroupChannel = tacitRoomListViewModel::createGroupChannel,
            onSetOpen = { createGroupChannelDialogOpen = it },
        )
    }
}

@Composable
private fun AutoDismissError(
    message: String?,
    errorType: ErrorType,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(message, errorType) {
        if (message == null || errorType != ErrorType.JUST_DISMISS) return@LaunchedEffect
        delay(ERROR_AUTO_DISMISS_MS)
        onDismiss()
    }
}

@Composable
private fun RoomListContent(
    roomListViewModel: RoomListViewModel,
    i18n: TacitI18nView,
    guilds: List<GuildEntry>,
    guildAvatars: Map<String, ByteArray?>,
    mode: RoomListMode,
    selectedRoomId: RoomId?,
    visibleRooms: List<TacitRoomListElementViewModel>,
    inviteRooms: List<TacitRoomListElementViewModel>,
    allRoomsEmpty: Boolean,
    canCreateNewRoomWithAccount: Boolean,
    searchResultsEmpty: Boolean,
    dmUnreadCount: Int,
    guildUnreadCounts: Map<String, Int>,
    selectedGuildAccountAvailable: Boolean,
    error: String?,
    errorType: ErrorType,
    onDismissError: () -> Unit,
    syncStates: UserSyncStates,
    selectedGuildInviteFallback: GuildEntry?,
    onAcceptSelectedGuildInvite: () -> Unit,
    onDeclineSelectedGuildInvite: () -> Unit,
    onSelectGuild: (GuildEntry?) -> Unit,
    onReorderGuild: (fromIndex: Int, toIndex: Int) -> Unit,
    onOpenCreateGuild: () -> Unit,
    onCreateRoom: () -> Unit,
    onCreateGroupChannel: () -> Unit,
    onOpenBrowseChannels: () -> Unit,
    onOpenInviteToGuild: () -> Unit,
    onOpenGuildSettings: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(tacitBackground)
            .padding(8.dp)
    ) {
        GuildRail(
            guilds = guilds,
            guildAvatars = guildAvatars,
            selectedGuild = mode.selectedGuildOrNull,
            dmUnreadCount = dmUnreadCount,
            guildUnreadCounts = guildUnreadCounts,
            onSelectGuild = onSelectGuild,
            onReorderGuild = onReorderGuild,
            onCreateGuild = onOpenCreateGuild,
        )

        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(tacitSurface)
        ) {
            GuildHero(
                i18n = i18n,
                selectedGuild = mode.selectedGuildOrNull,
                visibleRoomsCount = visibleRooms.size,
            )

            when (mode) {
                is RoomListMode.DirectMessages -> {
                    DmChannelToolbar(
                        i18n = i18n,
                        roomListViewModel = roomListViewModel,
                        canCreateRoom = canCreateNewRoomWithAccount,
                        onCreateRoom = onCreateRoom,
                        onCreateGroupChannel = onCreateGroupChannel,
                    )
                }

                is RoomListMode.GuildChannels -> {
                    GuildChannelToolbar(
                        i18n = i18n,
                        roomListViewModel,
                        canCreateNewRoomWithAccount,
                        onCreateRoom,
                        onOpenBrowseChannels,
                        onOpenInviteToGuild,
                        selectedGuildAccountAvailable,
                        onOpenGuildSettings,
                        true
                    )
                }
            }

            OfflineWarningBanner(i18n, syncStates)
            RoomListErrors(
                error = error,
                errorType = errorType,
                onDismissError = onDismissError,
            )
            HorizontalDivider(color = tacitBorder)

            RoomListBody(
                roomListViewModel = roomListViewModel,
                i18n = i18n,
                mode = mode,
                selectedRoomId = selectedRoomId,
                visibleRooms = visibleRooms,
                inviteRooms = inviteRooms,
                allRoomsEmpty = allRoomsEmpty,
                canCreateNewRoomWithAccount = canCreateNewRoomWithAccount,
                searchResultsEmpty = searchResultsEmpty,
                onBrowseRooms = onOpenBrowseChannels,
                selectedGuildInviteRoomId = selectedGuildInviteFallback?.roomId,
                selectedGuildInviteName = selectedGuildInviteFallback?.displayName,
                onAcceptSelectedGuildInvite = onAcceptSelectedGuildInvite,
                onDeclineSelectedGuildInvite = onDeclineSelectedGuildInvite,
            )
        }
    }
}

@Composable
private fun OfflineWarningBanner(
    i18n: TacitI18nView,
    syncStates: UserSyncStates,
) {
    if (syncStates.failedFor.isEmpty()) return
    val title = i18n.tacitOfflineTitle()
    val description = if (syncStates.failedForAll) {
        i18n.tacitOfflineAllDescription()
    } else {
        i18n.tacitOfflineSomeDescription(syncStates.joinFailedToString())
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tacitWarningBg)
            .border(1.dp, tacitWarningBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = tacitWarningText,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = tacitWarningText,
        )
    }
}

@Composable
private fun RoomListErrors(
    error: String?,
    errorType: ErrorType,
    onDismissError: () -> Unit,
) {
    AutoDismissError(error, errorType, onDismissError)
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 14.dp),
            maxLines = 3
        )
        Spacer(Modifier.height(6.dp))
    }
}
