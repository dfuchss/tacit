package org.fuchss.matrix.tacit.views.room.list.dialogs.browse

import androidx.compose.runtime.Composable
import de.connect2x.trixnity.core.model.RoomId
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun BrowseChannelsDialogContainer(
    open: Boolean,
    joiningRoomId: RoomId?,
    error: String?,
    selectedGuild: GuildEntry?,
    browseChannels: List<SpaceChannelEntry>,
    canJoinChannels: Boolean,
    onJoinChannel: (guild: GuildEntry, channel: SpaceChannelEntry, onSuccess: () -> Unit) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return
    val submission = rememberTacitDialogSubmission(error)

    BrowseChannelsDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        channels = browseChannels,
        canJoinChannels = canJoinChannels,
        joiningRoomId = joiningRoomId,
        onDismiss = { if (joiningRoomId == null) onSetOpen(false) },
        onJoinChannel = { channel ->
            if (!channel.isJoinable || channel.isJoined) return@BrowseChannelsDialog
            submission.submit()
            onJoinChannel(guild, channel) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
