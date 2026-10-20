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
        val viewModel = roomListViewModel as? TacitRoomListViewModel
            ?: error("TacitRoomListView requires RoomListViewModelFactory to provide TacitRoomListViewModel.")
        val i18n = DI.get<TacitI18nView>()
        LaunchedEffect(Unit) {
            roomListViewModel.showSearch.value = false
        }

        val state by viewModel.uiState.collectAsState()
        val dialogs = remember { RoomListDialogState() }
        val actions = remember(viewModel, i18n, dialogs) { TacitRoomListViewActions(viewModel, i18n, dialogs) }

        AutoDismissError(state.error.message, state.error.type, actions::dismissError)

        LaunchedEffect(state.selectedGuild, state.rail.guilds) {
            viewModel.refreshSelectedGuild(state.rail.guilds)
        }

        RoomListContent(state = state, actions = actions)
        RoomListDialogs(viewModel = viewModel, state = state, dialogs = dialogs)
    }
}

@Composable
private fun RoomListDialogs(
    viewModel: TacitRoomListViewModel,
    state: TacitRoomListUiState,
    dialogs: RoomListDialogState,
) {
    val selectedGuild = state.selectedGuild
    val canUseSelectedGuildAccount = state.status.canUseSelectedGuildAccount
    val canUsePreferredCreationAccount = state.status.canUsePreferredCreationAccount

    CreateGuildDialogContainer(
        open = dialogs.createGuild,
        viewModel = viewModel.createGuildViewModel,
        canCreateGuild = canUsePreferredCreationAccount,
        onSetOpen = { dialogs.createGuild = it },
    )

    CreateChannelDialogContainer(
        open = dialogs.createChannel,
        viewModel = viewModel.createChannelViewModel,
        selectedGuild = selectedGuild,
        canCreateChannel = canUseSelectedGuildAccount,
        onSetOpen = { dialogs.createChannel = it },
    )

    BrowseChannelsDialogContainer(
        open = dialogs.browseChannels,
        viewModel = viewModel.browseChannelsViewModel,
        selectedGuild = selectedGuild,
        browseChannels = state.browseChannels,
        canJoinChannels = canUseSelectedGuildAccount,
        onSetOpen = { dialogs.browseChannels = it },
    )

    InviteToGuildDialogContainer(
        open = dialogs.inviteToGuild,
        viewModel = viewModel.inviteToGuildViewModel,
        selectedGuild = selectedGuild,
        canInviteMember = canUseSelectedGuildAccount,
        canSearchUsers = canUseSelectedGuildAccount,
        onSetOpen = { dialogs.inviteToGuild = it },
    )

    CreateDirectMessageDialogContainer(
        open = dialogs.createDirectMessage,
        viewModel = viewModel.createDirectMessageViewModel,
        canStartDirectMessage = canUsePreferredCreationAccount,
        canSearchUsers = canUsePreferredCreationAccount,
        onSetOpen = { dialogs.createDirectMessage = it },
    )

    CreateGroupChannelDialogContainer(
        open = dialogs.createGroupChannel,
        viewModel = viewModel.createGroupChannelViewModel,
        canCreateGroupChannel = canUsePreferredCreationAccount,
        onSetOpen = { dialogs.createGroupChannel = it },
    )

    CreateCategoryDialogContainer(
        open = dialogs.createCategory,
        viewModel = viewModel.createCategoryViewModel,
        selectedGuild = selectedGuild,
        canCreateCategory = canUseSelectedGuildAccount,
        onSetOpen = { dialogs.createCategory = it },
    )
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
    state: TacitRoomListUiState,
    actions: RoomListActions,
    modifier: Modifier = Modifier,
) {
    val i18n = DI.get<TacitI18nView>()
    val mode = state.mode
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.tacit.background)
            .padding(TacitSpacing.appPadding)
    ) {
        GuildRail(
            state = state.rail,
            onSelectGuild = actions::selectGuild,
            onReorderGuild = actions::reorderGuild,
            onCreateGuild = actions::openCreateGuild,
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
                    visibleRoomsCount = state.rooms.visibleRooms.size,
                )

                when (mode) {
                    is RoomListMode.DirectMessages -> {
                        DmChannelToolbar(
                            i18n = i18n,
                            searchTerm = actions.searchTerm,
                            canCreateRoom = state.status.canCreateNewRoomWithAccount,
                            onCreateRoom = actions::openCreateRoom,
                            onCreateGroupChannel = actions::openCreateGroupChannel,
                        )
                    }

                    is RoomListMode.GuildChannels -> {
                        GuildChannelToolbar(
                            i18n = i18n,
                            searchTerm = actions.searchTerm,
                            canCreateRoom = state.status.canCreateNewRoomWithAccount,
                            onCreateRoom = actions::openCreateRoom,
                            onCreateCategory = actions::openCreateCategory,
                            onBrowseChannels = actions::openBrowseChannels,
                            onInviteToGuild = actions::openInviteToGuild,
                            canInviteToGuild = state.status.canUseSelectedGuildAccount,
                            onOpenGuildSettings = { actions.openSpaceSettings(mode.guild.roomId) },
                            // Deliberately not gated on power levels: guild settings is also where members see the
                            // member list and leave the guild; the editable fields inside gate themselves.
                            canEditGuildSettings = true,
                        )
                    }
                }

                OfflineWarningBanner(i18n, state.status.syncStates)
                RoomListErrors(error = state.error.message)
                HorizontalDivider(color = MaterialTheme.tacit.border)

                RoomListBody(
                    i18n = i18n,
                    state = state,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun OfflineWarningBanner(
    i18n: TacitI18nView,
    syncStates: UserSyncStates,
    modifier: Modifier = Modifier,
) {
    if (syncStates.failedFor.isEmpty()) return
    val title = i18n.tacitOfflineTitle()
    val description = if (syncStates.failedForAll) {
        i18n.tacitOfflineAllDescription()
    } else {
        i18n.tacitOfflineSomeDescription(syncStates.joinFailedToString())
    }
    Column(
        modifier = modifier
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
    modifier: Modifier = Modifier,
) {
    // Auto-dismissed by the single AutoDismissError at the top of the room list.
    if (error != null) {
        TacitCardSurface(
            modifier = modifier
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
