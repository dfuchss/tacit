package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.createChannel

@Composable
internal fun CreateChannelDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    roomListViewModel: RoomListViewModel,
    selectedGuild: GuildEntry?,
    selectedGuildClient: MatrixClient?,
    scope: CoroutineScope,
    onSetOpen: (Boolean) -> Unit,
    onSetInProgress: (Boolean) -> Unit,
    onSetError: (String?) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return

    CreateChannelDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isCreating = inProgress,
        canCreateChannel = selectedGuildClient != null,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateChannel = { name, topic ->
            if (selectedGuildClient == null) {
                onSetOpen(false)
                onSetError("Could not resolve active guild context.")
                return@CreateChannelDialog
            }
            scope.launch {
                onSetInProgress(true)
                onSetError(null)
                guild.createChannel(
                    matrixClient = selectedGuildClient,
                    channelName = name,
                    channelTopic = topic,
                ).fold(
                    onSuccess = { newRoomId ->
                        onSetOpen(false)
                        roomListViewModel.selectRoom(newRoomId)
                    },
                    onFailure = { throwable ->
                        onSetOpen(false)
                        onSetError("Could not create room: ${throwable.message ?: "Unknown error"}")
                    },
                )
                onSetInProgress(false)
            }
        },
    )
}
