package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.TacitAction
import org.fuchss.matrix.tacit.viewmodel.util.TacitErrorSource
import org.fuchss.matrix.tacit.viewmodel.util.acceptRoomInvite
import org.fuchss.matrix.tacit.viewmodel.util.declineRoomInvite
import org.fuchss.matrix.tacit.viewmodel.util.moveRoomToCategory

/** The room list's guild actions that are not dialogs: answering a guild invite and dragging a room into a category. */
internal class TacitRoomListActions(
    scope: CoroutineScope,
    private val i18n: TacitI18n,
    private val onGuildSelected: (GuildEntry?) -> Unit,
    private val onRoomSelected: (RoomId) -> Unit,
    private val guildClient: (GuildEntry) -> MatrixClient?,
) {
    private val guildInviteAction = TacitAction(scope, i18n)
    private val moveRoomAction = TacitAction(scope, i18n)

    val guildInviteActionInProgress: StateFlow<Boolean> = guildInviteAction.isCreating
    val moveRoomInProgress: StateFlow<Boolean> = moveRoomAction.isCreating
    val errorSources: List<TacitErrorSource> = listOf(guildInviteAction, moveRoomAction)

    private fun noAccountForGuildInvite() =
        Result.failure<Nothing>(IllegalStateException(i18n.tacitNoAccountForGuildInvite()))

    fun acceptGuildInvite(guild: GuildEntry) {
        guildInviteAction.launch(TacitI18n::tacitAcceptInviteFailed, onSuccess = onRoomSelected) {
            guildClient(guild)?.acceptRoomInvite(roomId = guild.roomId) ?: noAccountForGuildInvite()
        }
    }

    fun declineGuildInvite(guild: GuildEntry) {
        guildInviteAction.launch(TacitI18n::tacitDeclineInviteFailed, onSuccess = { _: Any? -> onGuildSelected(null) }) {
            guildClient(guild)?.declineRoomInvite(roomId = guild.roomId) ?: noAccountForGuildInvite()
        }
    }

    fun moveRoomToCategory(
        guild: GuildEntry,
        roomId: RoomId,
        targetCategoryRoomId: RoomId,
        currentCategoryRoomIds: Set<RoomId>,
    ) {
        moveRoomAction.launch(TacitI18n::tacitMoveRoomToCategoryFailed) {
            guildClient(guild)?.let { client ->
                moveRoomToCategory(
                    matrixClient = client,
                    roomId = roomId,
                    targetCategoryRoomId = targetCategoryRoomId,
                    currentCategoryRoomIds = currentCategoryRoomIds,
                )
            } ?: Result.failure(IllegalStateException(i18n.tacitNoGuildContext()))
        }
    }
}
