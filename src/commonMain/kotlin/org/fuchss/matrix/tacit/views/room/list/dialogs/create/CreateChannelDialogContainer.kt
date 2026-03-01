package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry

@Composable
internal fun CreateChannelDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    selectedGuild: GuildEntry?,
    canCreateChannel: Boolean,
    onCreateChannel: (guild: GuildEntry, name: String, topic: String) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return

    CreateChannelDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isCreating = inProgress,
        canCreateChannel = canCreateChannel,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateChannel = { name, topic ->
            onCreateChannel(guild, name, topic)
            onSetOpen(false)
        },
    )
}
