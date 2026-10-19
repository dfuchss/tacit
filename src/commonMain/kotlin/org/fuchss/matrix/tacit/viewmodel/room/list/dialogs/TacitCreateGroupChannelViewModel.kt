package org.fuchss.matrix.tacit.viewmodel.room.list.dialogs

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.util.createGroupChannel

/** "Create Group Chat" on the DM home: creates a room outside any guild and opens it. */
internal class TacitCreateGroupChannelViewModel(
    viewModelContext: ViewModelContext,
    private val creationClient: () -> MatrixClient?,
    private val onRoomCreated: (RoomId) -> Unit,
) : TacitDialogViewModel(viewModelContext) {

    fun createGroupChannel(name: String, topic: String, onSuccess: () -> Unit) {
        action.launch(TacitI18n::tacitCreateGroupChatFailed, onSuccess = { roomId: RoomId ->
            onRoomCreated(roomId)
            onSuccess()
        }) {
            creationClient()?.createGroupChannel(roomName = name, roomTopic = topic)
                ?: Result.failure(IllegalStateException(tacitI18n.tacitNoActiveAccount()))
        }
    }
}
