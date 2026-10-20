package org.fuchss.matrix.tacit.views.room.list.dialogs.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.fuchss.matrix.tacit.viewmodel.room.list.dialogs.TacitBrowseChannelsViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun BrowseChannelsDialogContainer(
    open: Boolean,
    viewModel: TacitBrowseChannelsViewModel,
    selectedGuild: GuildEntry?,
    browseChannels: List<SpaceChannelEntry>,
    canJoinChannels: Boolean,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return
    val joiningRoomId = viewModel.joiningRoomId.collectAsState().value
    val submission = rememberTacitDialogSubmission(viewModel.failure.collectAsState().value)

    BrowseChannelsDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        channels = browseChannels,
        canJoinChannels = canJoinChannels,
        joiningRoomId = joiningRoomId,
        onDismiss = { if (joiningRoomId == null) onSetOpen(false) },
        onJoinChannel = { channel ->
            if (!channel.isJoinable || channel.isJoined) return@BrowseChannelsDialog
            submission.submit()
            viewModel.joinChannel(guild, channel) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
