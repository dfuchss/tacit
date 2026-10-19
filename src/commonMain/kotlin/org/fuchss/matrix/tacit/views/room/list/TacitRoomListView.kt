package org.fuchss.matrix.tacit.views.room.list

import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitCardSurface
import org.fuchss.matrix.tacit.ui.TacitPaneSurface
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.ui.TacitSpacing
import org.fuchss.matrix.tacit.viewmodel.room.list.*
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.list.dialogs.browse.BrowseChannelsDialogContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.create.CreateCategoryDialogContainer
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
        val guildChannelGroups = tacitRoomListViewModel.guildChannelGroups.collectAsState().value
        val inviteRooms = tacitRoomListViewModel.inviteRooms.collectAsState().value
        val dmUnreadCount = tacitRoomListViewModel.dmUnreadCount.collectAsState().value
        val guildUnreadCounts = tacitRoomListViewModel.guildUnreadCounts.collectAsState().value
        val browseChannels = tacitRoomListViewModel.browseChannelsViewModel.channels.collectAsState().value
        val selectedGuild = tacitRoomListViewModel.selectedGuild.collectAsState().value
        val selectedGuildInviteFallback =
            selectedGuild?.takeIf { guild -> guild.isInvite && inviteRooms.none { it.roomId == guild.roomId } }
        val selectedRoomId = roomListViewModel.selectedRoomId.collectAsState().value
        val canCreateNewRoomWithAccount = roomListViewModel.canCreateNewRoomWithAccount.collectAsState().value
        val searchResultsEmpty = roomListViewModel.searchResultsEmpty.collectAsState().value
        val error = tacitRoomListViewModel.error.collectAsState().value
        val errorType = tacitRoomListViewModel.errorType.collectAsState().value
        val canUseSelectedGuildAccount = tacitRoomListViewModel.canUseSelectedGuildAccount.collectAsState().value
        val canUsePreferredCreationAccount =
            tacitRoomListViewModel.canUsePreferredCreationAccount.collectAsState().value
        val reorderCategoriesInProgress = tacitRoomListViewModel.reorderCategoriesInProgress.collectAsState().value
        val guildInviteActionInProgress = tacitRoomListViewModel.guildInviteActionInProgress.collectAsState().value
        val syncStates = roomListViewModel.syncStates.collectAsState().value

        var createGuildDialogOpen by remember { mutableStateOf(false) }
        var createChannelDialogOpen by remember { mutableStateOf(false) }
        var browseChannelsDialogOpen by remember { mutableStateOf(false) }
        var inviteToGuildDialogOpen by remember { mutableStateOf(false) }
        var createDirectMessageDialogOpen by remember { mutableStateOf(false) }
        var createGroupChannelDialogOpen by remember { mutableStateOf(false) }
        var createCategoryDialogOpen by remember { mutableStateOf(false) }

        AutoDismissError(error, errorType) {
            roomListViewModel.errorDismiss()
        }

        LaunchedEffect(selectedGuild, guilds) {
            if (selectedGuild == null) return@LaunchedEffect
            val refreshedSelectedGuild = guilds.firstOrNull { guild ->
                guild.roomId == selectedGuild.roomId && guild.userId == selectedGuild.userId
            }
            if (refreshedSelectedGuild == null) {
                tacitRoomListViewModel.selectGuild(null)
            } else if (refreshedSelectedGuild != selectedGuild) {
                tacitRoomListViewModel.selectGuild(refreshedSelectedGuild)
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
            guildChannelGroups = guildChannelGroups,
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
            onOpenCreateCategory = {
                roomListViewModel.errorDismiss()
                createCategoryDialogOpen = true
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
                { tacitRoomListViewModel.openSpaceSettings(guild.roomId) }
            },
            onReorderCategory = { fromIndex, toIndex ->
                val guild = selectedGuild ?: return@RoomListContent
                if (reorderCategoriesInProgress) return@RoomListContent
                val ordered = guildChannelGroups.map { it.categoryRoomId }.toMutableList()
                if (fromIndex !in ordered.indices || toIndex !in ordered.indices || fromIndex == toIndex) {
                    return@RoomListContent
                }
                val moved = ordered.removeAt(fromIndex)
                ordered.add(toIndex, moved)
                tacitRoomListViewModel.reorderCategories(guild, ordered)
            },
            onOpenCategorySettings = tacitRoomListViewModel::openSpaceSettings,
        )

        CreateGuildDialogContainer(
            open = createGuildDialogOpen,
            viewModel = tacitRoomListViewModel.createGuildViewModel,
            canCreateGuild = canUsePreferredCreationAccount,
            onSetOpen = { createGuildDialogOpen = it },
        )

        CreateChannelDialogContainer(
            open = createChannelDialogOpen,
            viewModel = tacitRoomListViewModel.createChannelViewModel,
            selectedGuild = selectedGuild,
            canCreateChannel = canUseSelectedGuildAccount,
            onSetOpen = { createChannelDialogOpen = it },
        )

        BrowseChannelsDialogContainer(
            open = browseChannelsDialogOpen,
            viewModel = tacitRoomListViewModel.browseChannelsViewModel,
            selectedGuild = selectedGuild,
            browseChannels = browseChannels,
            canJoinChannels = canUseSelectedGuildAccount,
            onSetOpen = { browseChannelsDialogOpen = it },
        )

        InviteToGuildDialogContainer(
            open = inviteToGuildDialogOpen,
            viewModel = tacitRoomListViewModel.inviteToGuildViewModel,
            selectedGuild = selectedGuild,
            canInviteMember = canUseSelectedGuildAccount,
            canSearchUsers = canUseSelectedGuildAccount,
            onSetOpen = { inviteToGuildDialogOpen = it },
        )

        CreateDirectMessageDialogContainer(
            open = createDirectMessageDialogOpen,
            viewModel = tacitRoomListViewModel.createDirectMessageViewModel,
            canStartDirectMessage = canUsePreferredCreationAccount,
            canSearchUsers = canUsePreferredCreationAccount,
            onSetOpen = { createDirectMessageDialogOpen = it },
        )

        CreateGroupChannelDialogContainer(
            open = createGroupChannelDialogOpen,
            viewModel = tacitRoomListViewModel.createGroupChannelViewModel,
            canCreateGroupChannel = canUsePreferredCreationAccount,
            onSetOpen = { createGroupChannelDialogOpen = it },
        )

        CreateCategoryDialogContainer(
            open = createCategoryDialogOpen,
            viewModel = tacitRoomListViewModel.createCategoryViewModel,
            selectedGuild = selectedGuild,
            canCreateCategory = canUseSelectedGuildAccount,
            onSetOpen = { createCategoryDialogOpen = it },
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
    guildChannelGroups: List<TacitGuildChannelGroup>,
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
    onOpenCreateCategory: () -> Unit,
    onCreateGroupChannel: () -> Unit,
    onOpenBrowseChannels: () -> Unit,
    onOpenInviteToGuild: () -> Unit,
    onOpenGuildSettings: (() -> Unit)?,
    onReorderCategory: (fromIndex: Int, toIndex: Int) -> Unit,
    onOpenCategorySettings: (RoomId) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.tacit.background)
            .padding(TacitSpacing.appPadding)
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

        Spacer(Modifier.width(TacitSpacing.paneGap))

        TacitPaneSurface(
            modifier = Modifier.fillMaxSize(),
            shape = TacitShapes.pane,
            innerShape = TacitShapes.paneInner,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                            roomListViewModel = roomListViewModel,
                            canCreateRoom = canCreateNewRoomWithAccount,
                            onCreateRoom = onCreateRoom,
                            onCreateCategory = onOpenCreateCategory,
                            onBrowseChannels = onOpenBrowseChannels,
                            onInviteToGuild = onOpenInviteToGuild,
                            canInviteToGuild = selectedGuildAccountAvailable,
                            onOpenGuildSettings = onOpenGuildSettings,
                            // Deliberately not gated on power levels: guild settings is also where members see the
                            // member list and leave the guild; the editable fields inside gate themselves.
                            canEditGuildSettings = true,
                        )
                    }
                }

                OfflineWarningBanner(i18n, syncStates)
                RoomListErrors(
                    error = error,
                    errorType = errorType,
                    onDismissError = onDismissError,
                )
                HorizontalDivider(color = MaterialTheme.tacit.border)

                RoomListBody(
                    roomListViewModel = roomListViewModel,
                    i18n = i18n,
                    mode = mode,
                    selectedRoomId = selectedRoomId,
                    visibleRooms = visibleRooms,
                    guildChannelGroups = guildChannelGroups,
                    inviteRooms = inviteRooms,
                    allRoomsEmpty = allRoomsEmpty,
                    canCreateNewRoomWithAccount = canCreateNewRoomWithAccount,
                    searchResultsEmpty = searchResultsEmpty,
                    onBrowseRooms = onOpenBrowseChannels,
                    onReorderCategory = onReorderCategory,
                    onOpenCategorySettings = onOpenCategorySettings,
                    selectedGuildInviteRoomId = selectedGuildInviteFallback?.roomId,
                    selectedGuildInviteName = selectedGuildInviteFallback?.displayName,
                    onAcceptSelectedGuildInvite = onAcceptSelectedGuildInvite,
                    onDeclineSelectedGuildInvite = onDeclineSelectedGuildInvite,
                )
            }
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
            .clip(TacitShapes.compact)
            .background(MaterialTheme.tacit.warningBg)
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.warningBorder, TacitShapes.compact)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.tacit.warningText,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.tacit.warningText,
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
        TacitCardSurface(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            shape = TacitShapes.compact,
            backgroundColor = MaterialTheme.tacit.errorBg,
            borderColor = MaterialTheme.tacit.errorBorder,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = error,
                color = MaterialTheme.tacit.errorText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
            )
        }
    }
}
