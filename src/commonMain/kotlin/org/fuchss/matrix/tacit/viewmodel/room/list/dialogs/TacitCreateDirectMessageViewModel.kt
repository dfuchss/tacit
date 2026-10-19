package org.fuchss.matrix.tacit.viewmodel.room.list.dialogs

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.util.matrixUserDirectorySearch
import org.fuchss.matrix.tacit.viewmodel.util.findOrCreateDM

/** "Start Direct Message": finds the user in the directory, then opens the existing DM or creates one. */
internal class TacitCreateDirectMessageViewModel(
    viewModelContext: ViewModelContext,
    private val creationClient: () -> MatrixClient?,
    private val onRoomCreated: (RoomId) -> Unit,
) : TacitDialogViewModel(viewModelContext) {

    val userSearch = matrixUserDirectorySearch(coroutineScope, tacitI18n)

    fun searchUsers(query: String) = userSearch.search(query, creationClient())

    fun startDirectMessage(userId: String, onSuccess: () -> Unit) {
        action.launch(TacitI18n::tacitStartDirectMessageFailed, onSuccess = { roomId: RoomId ->
            onRoomCreated(roomId)
            onSuccess()
        }) {
            when {
                !UserId.isValid(userId) -> Result.failure(IllegalArgumentException(tacitI18n.tacitInvalidUserId(userId)))
                else -> creationClient()?.let { client -> UserId(userId).findOrCreateDM(client) }
                    ?: Result.failure(IllegalStateException(tacitI18n.tacitNoActiveAccount()))
            }
        }
    }
}
