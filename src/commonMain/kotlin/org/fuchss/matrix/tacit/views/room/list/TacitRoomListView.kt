package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.background
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
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.roomlist.RoomListView
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import de.connect2x.trixnity.messenger.viewmodel.util.ErrorType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.TacitRoomNavigationState
import org.fuchss.matrix.tacit.tacitBorder
import org.fuchss.matrix.tacit.tacitLayer
import org.fuchss.matrix.tacit.tacitShell
import org.fuchss.matrix.tacit.viewmodel.room.list.RoomListMode
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListElementViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.selectedGuildOrNull
import org.fuchss.matrix.tacit.views.room.list.dialogs.browse.BrowseChannelsDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.create.CreateChannelDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.create.CreateGuildDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.direct.CreateDirectMessageDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.invite.InviteToGuildDialogContainer

private const val ERROR_AUTO_DISMISS_MS = 8_000L

class TacitRoomListView : RoomListView {
    @Composable
    override fun create(roomListViewModel: RoomListViewModel) {
        val tacitRoomListViewModel = roomListViewModel as? TacitRoomListViewModel
            ?: error("TacitRoomListView requires RoomListViewModelFactory to provide TacitRoomListViewModel.")
        val i18n = DI.get<I18nView>()
        val scope = rememberCoroutineScope()
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
        val selectedGuildInviteFallback = tacitRoomListViewModel.selectedGuildInviteFallback.collectAsState().value
        val selectedGuild = tacitRoomListViewModel.selectedGuild.collectAsState().value
        val selectedRoomId = roomListViewModel.selectedRoomId.collectAsState().value
        val pendingRoomIdToOpen = TacitRoomNavigationState.openRoomForRoomId
        val canCreateNewRoomWithAccount = roomListViewModel.canCreateNewRoomWithAccount.collectAsState().value
        val searchResultsEmpty = roomListViewModel.searchResultsEmpty.collectAsState().value
        val error = tacitRoomListViewModel.error.collectAsState().value
        val errorType = tacitRoomListViewModel.errorType.collectAsState().value
        val selectedGuildClient = tacitRoomListViewModel.selectedGuildClient.collectAsState().value
        val preferredCreationClient = tacitRoomListViewModel.preferredCreationClient.collectAsState().value

        LaunchedEffect(pendingRoomIdToOpen, tacitRooms) {
            val pendingRoomId = pendingRoomIdToOpen ?: return@LaunchedEffect
            val targetRoom = tacitRooms.firstOrNull { it.roomId.full == pendingRoomId } ?: return@LaunchedEffect
            roomListViewModel.selectRoom(targetRoom.roomId)
            TacitRoomNavigationState.openRoomForRoomId = null
            TacitRoomNavigationState.showMembersPane = false
        }

        var createGuildDialogOpen by remember { mutableStateOf(false) }
        var guildCreationInProgress by remember { mutableStateOf(false) }
        var createChannelDialogOpen by remember { mutableStateOf(false) }
        var channelCreationInProgress by remember { mutableStateOf(false) }
        var browseChannelsDialogOpen by remember { mutableStateOf(false) }
        var browseChannelsJoinInProgress by remember { mutableStateOf<RoomId?>(null) }
        var inviteToGuildDialogOpen by remember { mutableStateOf(false) }
        var guildInviteInProgress by remember { mutableStateOf(false) }
        var createDirectMessageDialogOpen by remember { mutableStateOf(false) }
        var directMessageCreationInProgress by remember { mutableStateOf(false) }

        AutoDismissError(error, errorType) {
            roomListViewModel.errorDismiss()
        }

        LaunchedEffect(selectedGuild, selectedGuildClient) {
            val currentSelectedGuild = selectedGuild ?: return@LaunchedEffect
            if (selectedGuildClient == null) {
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
            selectedGuildClientAvailable = selectedGuildClient != null,
            error = error,
            errorType = errorType,
            onDismissError = { roomListViewModel.errorDismiss() },
            selectedGuildInviteFallback = selectedGuildInviteFallback,
            onAcceptSelectedGuildInvite = {
                val targetGuild = selectedGuildInviteFallback
                val targetClient = selectedGuildClient
                if (targetGuild == null || targetClient == null) {
                    tacitRoomListViewModel.reportError("No account available for this guild invite.")
                    return@RoomListContent
                }
                scope.launch {
                    tacitRoomListViewModel.reportError(null)
                    tacitRoomListViewModel.acceptGuildInvite(targetGuild).fold(
                        onSuccess = { joinedRoomId ->
                            roomListViewModel.selectRoom(joinedRoomId)
                        },
                        onFailure = { throwable ->
                            tacitRoomListViewModel.reportError("Could not accept invite: ${throwable.message ?: "Unknown error"}")
                        }
                    )
                }
            },
            onDeclineSelectedGuildInvite = {
                val targetGuild = selectedGuildInviteFallback
                val targetClient = selectedGuildClient
                if (targetGuild == null || targetClient == null) {
                    tacitRoomListViewModel.reportError("No account available for this guild invite.")
                    return@RoomListContent
                }
                scope.launch {
                    tacitRoomListViewModel.reportError(null)
                    tacitRoomListViewModel.declineGuildInvite(targetGuild).fold(
                        onSuccess = {
                            tacitRoomListViewModel.selectGuild(null)
                        },
                        onFailure = { throwable ->
                            tacitRoomListViewModel.reportError("Could not decline invite: ${throwable.message ?: "Unknown error"}")
                        }
                    )
                }
            },
            onSelectGuild = { tacitRoomListViewModel.selectGuild(it) },
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
            inProgress = guildCreationInProgress,
            preferredCreationClient = preferredCreationClient,
            scope = scope,
            onSetOpen = { createGuildDialogOpen = it },
            onSetInProgress = { guildCreationInProgress = it },
            onSetError = { tacitRoomListViewModel.reportError(it) },
            onSetSelectedGuild = { tacitRoomListViewModel.selectGuild(it) },
        )

        CreateChannelDialogContainer(
            open = createChannelDialogOpen,
            inProgress = channelCreationInProgress,
            roomListViewModel = roomListViewModel,
            selectedGuild = selectedGuild,
            selectedGuildClient = selectedGuildClient,
            scope = scope,
            onSetOpen = { createChannelDialogOpen = it },
            onSetInProgress = { channelCreationInProgress = it },
            onSetError = { tacitRoomListViewModel.reportError(it) },
        )

        BrowseChannelsDialogContainer(
            open = browseChannelsDialogOpen,
            joiningRoomId = browseChannelsJoinInProgress,
            roomListViewModel = roomListViewModel,
            selectedGuild = selectedGuild,
            selectedGuildClient = selectedGuildClient,
            browseChannels = browseChannels,
            scope = scope,
            onSetOpen = { browseChannelsDialogOpen = it },
            onSetJoiningRoomId = { browseChannelsJoinInProgress = it },
            onSetError = { tacitRoomListViewModel.reportError(it) },
        )

        InviteToGuildDialogContainer(
            open = inviteToGuildDialogOpen,
            inProgress = guildInviteInProgress,
            selectedGuild = selectedGuild,
            selectedGuildClient = selectedGuildClient,
            scope = scope,
            onSetOpen = { inviteToGuildDialogOpen = it },
            onSetInProgress = { guildInviteInProgress = it },
            onSetError = { tacitRoomListViewModel.reportError(it) },
        )

        CreateDirectMessageDialogContainer(
            open = createDirectMessageDialogOpen,
            inProgress = directMessageCreationInProgress,
            roomListViewModel = roomListViewModel,
            preferredCreationClient = preferredCreationClient,
            scope = scope,
            onSetOpen = { createDirectMessageDialogOpen = it },
            onSetInProgress = { directMessageCreationInProgress = it },
            onSetError = { tacitRoomListViewModel.reportError(it) },
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
    i18n: I18nView,
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
    selectedGuildClientAvailable: Boolean,
    error: String?,
    errorType: ErrorType,
    onDismissError: () -> Unit,
    selectedGuildInviteFallback: GuildEntry?,
    onAcceptSelectedGuildInvite: () -> Unit,
    onDeclineSelectedGuildInvite: () -> Unit,
    onSelectGuild: (GuildEntry?) -> Unit,
    onOpenCreateGuild: () -> Unit,
    onCreateRoom: () -> Unit,
    onOpenBrowseChannels: () -> Unit,
    onOpenInviteToGuild: () -> Unit,
    onOpenGuildSettings: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(tacitShell)
            .padding(8.dp)
    ) {
        GuildRail(
            guilds = guilds,
            guildAvatars = guildAvatars,
            selectedGuild = mode.selectedGuildOrNull,
            dmUnreadCount = dmUnreadCount,
            guildUnreadCounts = guildUnreadCounts,
            onSelectGuild = onSelectGuild,
            onCreateGuild = onOpenCreateGuild,
        )

        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(tacitLayer)
        ) {
            GuildHero(
                selectedGuild = mode.selectedGuildOrNull,
                visibleRoomsCount = visibleRooms.size,
            )

            when (mode) {
                is RoomListMode.DirectMessages -> {
                    DmChannelToolbar(roomListViewModel, canCreateNewRoomWithAccount, onCreateRoom)
                }

                is RoomListMode.GuildChannels -> {
                    GuildChannelToolbar(
                        roomListViewModel,
                        canCreateNewRoomWithAccount,
                        onCreateRoom,
                        onOpenBrowseChannels,
                        onOpenInviteToGuild,
                        selectedGuildClientAvailable,
                        onOpenGuildSettings,
                        true
                    )
                }
            }

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
