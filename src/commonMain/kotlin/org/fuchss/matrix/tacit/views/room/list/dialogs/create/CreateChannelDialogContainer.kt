package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateChannelDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    error: String?,
    selectedGuild: GuildEntry?,
    canCreateChannel: Boolean,
    onCreateChannel: (guild: GuildEntry, name: String, topic: String, onSuccess: () -> Unit) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return
    val submission = rememberTacitDialogSubmission(error)

    CreateChannelDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isCreating = inProgress,
        canCreateChannel = canCreateChannel,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateChannel = { name, topic ->
            submission.submit()
            onCreateChannel(guild, name, topic) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
