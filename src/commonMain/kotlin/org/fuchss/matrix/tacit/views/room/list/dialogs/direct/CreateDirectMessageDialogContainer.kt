package org.fuchss.matrix.tacit.views.room.list.dialogs.direct

import androidx.compose.runtime.Composable
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.util.findOrCreateDM
import org.fuchss.matrix.tacit.viewmodel.util.searchUserDirectory

@Composable
internal fun CreateDirectMessageDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    roomListViewModel: RoomListViewModel,
    preferredCreationClient: MatrixClient?,
    scope: CoroutineScope,
    onSetOpen: (Boolean) -> Unit,
    onSetInProgress: (Boolean) -> Unit,
    onSetError: (String?) -> Unit,
) {
    if (!open) return

    CreateDirectMessageDialog(
        isStartingDirectMessage = inProgress,
        canStartDirectMessage = preferredCreationClient != null,
        canSearchUsers = preferredCreationClient != null,
        searchUsers = { query ->
            preferredCreationClient?.searchUserDirectory(query)
                ?: Result.success(emptyList())
        },
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onStartDirectMessage = { userId ->
            if (preferredCreationClient == null) {
                onSetOpen(false)
                onSetError("No active Matrix account available.")
                return@CreateDirectMessageDialog
            }

            if (!(UserId.isValid(userId))) {
                onSetOpen(false)
                onSetError("No valid userId provided: '$userId'")
                return@CreateDirectMessageDialog
            }

            scope.launch {
                onSetInProgress(true)
                onSetError(null)
                UserId(userId).findOrCreateDM(
                    preferredCreationClient
                ).fold(
                    onSuccess = { roomId ->
                        onSetOpen(false)
                        roomListViewModel.selectRoom(roomId)
                    },
                    onFailure = { throwable ->
                        onSetOpen(false)
                        onSetError("Could not start direct message: ${throwable.message ?: "Unknown error"}")
                    },
                )
                onSetInProgress(false)
            }
        },
    )
}
