package org.fuchss.matrix.tacit.views.room.list.dialogs.invite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.fuchss.matrix.tacit.viewmodel.room.list.dialogs.TacitInviteToGuildViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun InviteToGuildDialogContainer(
    open: Boolean,
    viewModel: TacitInviteToGuildViewModel,
    selectedGuild: GuildEntry?,
    canInviteMember: Boolean,
    canSearchUsers: Boolean,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return
    val inProgress = viewModel.isCreating.collectAsState().value
    val submission = rememberTacitDialogSubmission(viewModel.failure.collectAsState().value)

    InviteToGuildDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isInviting = inProgress,
        canInviteMember = canInviteMember,
        canSearchUsers = canSearchUsers,
        userSearch = viewModel.userSearch.state.collectAsState().value,
        onSearchUsers = { query -> viewModel.searchUsers(guild, query) },
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onInviteMember = { userId, reason ->
            submission.submit()
            viewModel.inviteUser(guild, userId, reason) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
