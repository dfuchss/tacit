package org.fuchss.matrix.tacit.viewmodel.room.list.dialogs

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.createChannel

/** "Create Room" in a guild: creates a channel in the guild and opens it. */
internal class TacitCreateChannelViewModel(
    viewModelContext: ViewModelContext,
    private val guildClient: (GuildEntry) -> MatrixClient?,
    private val onRoomCreated: (RoomId) -> Unit,
) : TacitDialogViewModel(viewModelContext) {

    fun createChannel(guild: GuildEntry, name: String, topic: String, onSuccess: () -> Unit) {
        action.launch(TacitI18n::tacitCreateRoomFailed, onSuccess = { roomId: RoomId ->
            onRoomCreated(roomId)
            onSuccess()
        }) {
            guildClient(guild)?.let { client -> guild.createChannel(matrixClient = client, channelName = name, channelTopic = topic) }
                ?: Result.failure(IllegalStateException(tacitI18n.tacitNoGuildContext()))
        }
    }
}
