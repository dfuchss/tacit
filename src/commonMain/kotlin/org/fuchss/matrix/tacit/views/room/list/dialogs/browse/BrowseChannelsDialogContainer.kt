package org.fuchss.matrix.tacit.views.room.list.dialogs.browse

import androidx.compose.runtime.Composable
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry
import org.fuchss.matrix.tacit.viewmodel.util.joinRoom

@Composable
internal fun BrowseChannelsDialogContainer(
    open: Boolean,
    joiningRoomId: RoomId?,
    roomListViewModel: RoomListViewModel,
    selectedGuild: GuildEntry?,
    selectedGuildClient: MatrixClient?,
    browseChannels: List<SpaceChannelEntry>,
    scope: CoroutineScope,
    onSetOpen: (Boolean) -> Unit,
    onSetJoiningRoomId: (RoomId?) -> Unit,
    onSetError: (String?) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return

    BrowseChannelsDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        channels = browseChannels,
        canJoinChannels = selectedGuildClient != null,
        joiningRoomId = joiningRoomId,
        onDismiss = { if (joiningRoomId == null) onSetOpen(false) },
        onJoinChannel = { channel ->
            if (!channel.isJoinable || channel.isJoined) return@BrowseChannelsDialog
            if (selectedGuildClient == null) {
                onSetOpen(false)
                onSetError("No account available for this guild.")
                return@BrowseChannelsDialog
            }
            scope.launch {
                onSetJoiningRoomId(channel.roomId)
                onSetError(null)
                selectedGuildClient.joinRoom(
                    roomId = channel.roomId,
                    viaServers = channel.via,
                ).fold(
                    onSuccess = { joinedRoomId ->
                        onSetOpen(false)
                        roomListViewModel.selectRoom(joinedRoomId)
                    },
                    onFailure = { throwable ->
                        onSetOpen(false)
                        onSetError("Could not join room: ${throwable.message ?: "Unknown error"}")
                    },
                )
                onSetJoiningRoomId(null)
            }
        },
    )
}
