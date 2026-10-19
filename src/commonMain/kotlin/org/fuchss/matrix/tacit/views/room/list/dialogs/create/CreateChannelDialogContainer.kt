package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.fuchss.matrix.tacit.viewmodel.room.list.dialogs.TacitCreateChannelViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateChannelDialogContainer(
    open: Boolean,
    viewModel: TacitCreateChannelViewModel,
    selectedGuild: GuildEntry?,
    canCreateChannel: Boolean,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return
    val inProgress = viewModel.isCreating.collectAsState().value
    val submission = rememberTacitDialogSubmission(viewModel.error.collectAsState().value)

    CreateChannelDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isCreating = inProgress,
        canCreateChannel = canCreateChannel,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateChannel = { name, topic ->
            submission.submit()
            viewModel.createChannel(guild, name, topic) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
