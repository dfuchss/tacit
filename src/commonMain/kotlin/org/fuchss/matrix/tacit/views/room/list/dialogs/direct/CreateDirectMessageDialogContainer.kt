package org.fuchss.matrix.tacit.views.room.list.dialogs.direct

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.fuchss.matrix.tacit.viewmodel.room.list.dialogs.TacitCreateDirectMessageViewModel
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateDirectMessageDialogContainer(
    open: Boolean,
    viewModel: TacitCreateDirectMessageViewModel,
    canStartDirectMessage: Boolean,
    canSearchUsers: Boolean,
    onSetOpen: (Boolean) -> Unit,
) {
    if (!open) return
    val inProgress = viewModel.isCreating.collectAsState().value
    val submission = rememberTacitDialogSubmission(viewModel.error.collectAsState().value)

    CreateDirectMessageDialog(
        isStartingDirectMessage = inProgress,
        canStartDirectMessage = canStartDirectMessage,
        canSearchUsers = canSearchUsers,
        userSearch = viewModel.userSearch.state.collectAsState().value,
        onSearchUsers = viewModel::searchUsers,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onStartDirectMessage = { userId ->
            submission.submit()
            viewModel.startDirectMessage(userId) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
