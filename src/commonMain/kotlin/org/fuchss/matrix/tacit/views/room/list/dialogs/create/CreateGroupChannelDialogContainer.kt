package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable

@Composable
internal fun CreateGroupChannelDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    canCreateGroupChannel: Boolean,
    onCreateGroupChannel: (name: String, topic: String) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    if (!open) return

    CreateGroupChannelDialog(
        isCreating = inProgress,
        canCreateGroupChannel = canCreateGroupChannel,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateGroupChannel = { name, topic ->
            onCreateGroupChannel(name, topic)
            onSetOpen(false)
        },
    )
}
