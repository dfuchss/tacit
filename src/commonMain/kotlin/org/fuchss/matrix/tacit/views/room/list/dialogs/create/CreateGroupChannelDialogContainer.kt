package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.fuchss.matrix.tacit.viewmodel.room.list.dialogs.TacitCreateGroupChannelViewModel
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateGroupChannelDialogContainer(
    open: Boolean,
    viewModel: TacitCreateGroupChannelViewModel,
    canCreateGroupChannel: Boolean,
    onSetOpen: (Boolean) -> Unit,
) {
    if (!open) return
    val inProgress = viewModel.isCreating.collectAsState().value
    val submission = rememberTacitDialogSubmission(viewModel.error.collectAsState().value)

    CreateGroupChannelDialog(
        isCreating = inProgress,
        canCreateGroupChannel = canCreateGroupChannel,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateGroupChannel = { name, topic ->
            submission.submit()
            viewModel.createGroupChannel(name, topic) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
