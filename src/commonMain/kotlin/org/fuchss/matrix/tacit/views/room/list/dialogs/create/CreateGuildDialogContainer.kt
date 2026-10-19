package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateGuildDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    error: String?,
    canCreateGuild: Boolean,
    onCreateGuild: (name: String, topic: String, createDefaultChannel: Boolean, onSuccess: () -> Unit) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    if (!open) return
    val submission = rememberTacitDialogSubmission(error)

    CreateGuildDialog(
        isCreating = inProgress,
        canCreateGuild = canCreateGuild,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateGuild = { name, topic, addDefaultChannel ->
            submission.submit()
            onCreateGuild(name, topic, addDefaultChannel) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
