package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.room.TacitSpaceSettingsRequests
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.TacitAction
import org.fuchss.matrix.tacit.viewmodel.util.TacitErrorSource
import org.fuchss.matrix.tacit.viewmodel.util.acceptRoomInvite
import org.fuchss.matrix.tacit.viewmodel.util.declineRoomInvite

/** The room list's guild actions that are not dialogs: answering a guild invite. */
internal class TacitRoomListActions(
    scope: CoroutineScope,
    private val i18n: TacitI18n,
    private val onGuildSelected: (GuildEntry?) -> Unit,
    private val onRoomSelected: (RoomId) -> Unit,
    private val guildClient: (GuildEntry) -> MatrixClient?,
    private val spaceSettingsRequests: TacitSpaceSettingsRequests,
) {
    private val guildInviteAction = TacitAction(scope, i18n)

    val guildInviteActionInProgress: StateFlow<Boolean> = guildInviteAction.isCreating
    val errorSources: List<TacitErrorSource> = listOf(guildInviteAction)

    private fun noAccountForGuildInvite() =
        Result.failure<Nothing>(IllegalStateException(i18n.tacitNoAccountForGuildInvite()))

    /**
     * Answering a guild invite drops a settings request filed while it was an invite: upstream showed its join prompt
     * instead of a room view model, so nothing consumed the request, and accepting would then open the guild's SPACE
     * settings out of the blue.
     */
    fun acceptGuildInvite(guild: GuildEntry) {
        spaceSettingsRequests.discard(guild.roomId)
        guildInviteAction.launch(TacitI18n::tacitAcceptInviteFailed, onSuccess = onRoomSelected) {
            guildClient(guild)?.acceptRoomInvite(roomId = guild.roomId) ?: noAccountForGuildInvite()
        }
    }

    fun declineGuildInvite(guild: GuildEntry) {
        spaceSettingsRequests.discard(guild.roomId)
        guildInviteAction.launch(TacitI18n::tacitDeclineInviteFailed, onSuccess = { _: Any? -> onGuildSelected(null) }) {
            guildClient(guild)?.declineRoomInvite(roomId = guild.roomId) ?: noAccountForGuildInvite()
        }
    }
}
