package org.fuchss.matrix.tacit.views.room.list.dialogs.direct

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry

@Composable
internal fun CreateDirectMessageDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    canStartDirectMessage: Boolean,
    canSearchUsers: Boolean,
    searchUsers: suspend (query: String) -> Result<List<UserDirectoryEntry>>,
    onStartDirectMessage: (userId: String) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    if (!open) return

    CreateDirectMessageDialog(
        isStartingDirectMessage = inProgress,
        canStartDirectMessage = canStartDirectMessage,
        canSearchUsers = canSearchUsers,
        searchUsers = searchUsers,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onStartDirectMessage = { userId ->
            onStartDirectMessage(userId)
            onSetOpen(false)
        },
    )
}
