package org.fuchss.matrix.tacit.views.room.list.dialogs.direct

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateDirectMessageDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    error: String?,
    canStartDirectMessage: Boolean,
    canSearchUsers: Boolean,
    searchUsers: suspend (query: String) -> Result<List<UserDirectoryEntry>>,
    onStartDirectMessage: (userId: String, onSuccess: () -> Unit) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    if (!open) return
    val submission = rememberTacitDialogSubmission(error)

    CreateDirectMessageDialog(
        isStartingDirectMessage = inProgress,
        canStartDirectMessage = canStartDirectMessage,
        canSearchUsers = canSearchUsers,
        searchUsers = searchUsers,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onStartDirectMessage = { userId ->
            submission.submit()
            onStartDirectMessage(userId) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
