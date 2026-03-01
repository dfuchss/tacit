package org.fuchss.matrix.tacit.views.room.list.dialogs.invite

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry

@Composable
internal fun InviteToGuildDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    selectedGuild: GuildEntry?,
    canInviteMember: Boolean,
    canSearchUsers: Boolean,
    searchUsers: suspend (guild: GuildEntry, query: String) -> Result<List<UserDirectoryEntry>>,
    onInviteMember: (guild: GuildEntry, userId: String, reason: String) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return

    InviteToGuildDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isInviting = inProgress,
        canInviteMember = canInviteMember,
        canSearchUsers = canSearchUsers,
        searchUsers = { query -> searchUsers(guild, query) },
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onInviteMember = { userId, reason ->
            onInviteMember(guild, userId, reason)
            onSetOpen(false)
        },
    )
}
