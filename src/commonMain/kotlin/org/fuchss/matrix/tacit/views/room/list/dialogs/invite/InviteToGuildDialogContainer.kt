package org.fuchss.matrix.tacit.views.room.list.dialogs.invite

import androidx.compose.runtime.Composable
import de.connect2x.trixnity.client.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.inviteMember
import org.fuchss.matrix.tacit.viewmodel.util.searchUserDirectory

@Composable
internal fun InviteToGuildDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    selectedGuild: GuildEntry?,
    selectedGuildClient: MatrixClient?,
    scope: CoroutineScope,
    onSetOpen: (Boolean) -> Unit,
    onSetInProgress: (Boolean) -> Unit,
    onSetError: (String?) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return

    InviteToGuildDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isInviting = inProgress,
        canInviteMember = selectedGuildClient != null,
        canSearchUsers = selectedGuildClient != null,
        searchUsers = { query ->
            selectedGuildClient?.searchUserDirectory(query)
                ?: Result.success(emptyList())
        },
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onInviteMember = { userId, reason ->
            if (selectedGuildClient == null) {
                onSetOpen(false)
                onSetError("Could not resolve active guild context.")
                return@InviteToGuildDialog
            }
            scope.launch {
                onSetInProgress(true)
                onSetError(null)
                guild.inviteMember(
                    selectedGuildClient,
                    userId = userId,
                    reason = reason
                ).fold(
                    onSuccess = {
                        onSetOpen(false)
                    },
                    onFailure = { throwable ->
                        onSetOpen(false)
                        onSetError("Could not send invite: ${throwable.message ?: "Unknown error"}")
                    },
                )
                onSetInProgress(false)
            }
        },
    )
}
