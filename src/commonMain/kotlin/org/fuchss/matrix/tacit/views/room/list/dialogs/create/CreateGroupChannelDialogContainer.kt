package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateGroupChannelDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    error: String?,
    canCreateGroupChannel: Boolean,
    onCreateGroupChannel: (name: String, topic: String, onSuccess: () -> Unit) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    if (!open) return
    val submission = rememberTacitDialogSubmission(error)

    CreateGroupChannelDialog(
        isCreating = inProgress,
        canCreateGroupChannel = canCreateGroupChannel,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateGroupChannel = { name, topic ->
            submission.submit()
            onCreateGroupChannel(name, topic) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
