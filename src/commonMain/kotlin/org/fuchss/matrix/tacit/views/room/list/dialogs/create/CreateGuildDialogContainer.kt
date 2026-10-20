package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.fuchss.matrix.tacit.viewmodel.room.list.dialogs.TacitCreateGuildViewModel
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateGuildDialogContainer(
    open: Boolean,
    viewModel: TacitCreateGuildViewModel,
    canCreateGuild: Boolean,
    onSetOpen: (Boolean) -> Unit,
) {
    if (!open) return
    val inProgress = viewModel.isCreating.collectAsState().value
    val submission = rememberTacitDialogSubmission(viewModel.failure.collectAsState().value)

    CreateGuildDialog(
        isCreating = inProgress,
        canCreateGuild = canCreateGuild,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateGuild = { name, topic, addDefaultChannel ->
            submission.submit()
            viewModel.createGuild(name, topic, addDefaultChannel) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
