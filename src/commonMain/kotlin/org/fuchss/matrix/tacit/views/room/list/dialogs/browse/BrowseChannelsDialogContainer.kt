package org.fuchss.matrix.tacit.views.room.list.dialogs.browse

import androidx.compose.runtime.Composable
import de.connect2x.trixnity.core.model.RoomId
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry

@Composable
internal fun BrowseChannelsDialogContainer(
    open: Boolean,
    joiningRoomId: RoomId?,
    selectedGuild: GuildEntry?,
    browseChannels: List<SpaceChannelEntry>,
    canJoinChannels: Boolean,
    onJoinChannel: (guild: GuildEntry, channel: SpaceChannelEntry) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return

    BrowseChannelsDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        channels = browseChannels,
        canJoinChannels = canJoinChannels,
        joiningRoomId = joiningRoomId,
        onDismiss = { if (joiningRoomId == null) onSetOpen(false) },
        onJoinChannel = { channel ->
            if (!channel.isJoinable || channel.isJoined) return@BrowseChannelsDialog
            onJoinChannel(guild, channel)
            onSetOpen(false)
        },
    )
}
