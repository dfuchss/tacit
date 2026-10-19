package org.fuchss.matrix.tacit.views.room.list.dialogs.invite

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun InviteToGuildDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    error: String?,
    selectedGuild: GuildEntry?,
    canInviteMember: Boolean,
    canSearchUsers: Boolean,
    searchUsers: suspend (guild: GuildEntry, query: String) -> Result<List<UserDirectoryEntry>>,
    onInviteMember: (guild: GuildEntry, userId: String, reason: String, onSuccess: () -> Unit) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return
    val submission = rememberTacitDialogSubmission(error)

    InviteToGuildDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isInviting = inProgress,
        canInviteMember = canInviteMember,
        canSearchUsers = canSearchUsers,
        searchUsers = { query -> searchUsers(guild, query) },
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onInviteMember = { userId, reason ->
            submission.submit()
            onInviteMember(guild, userId, reason) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
