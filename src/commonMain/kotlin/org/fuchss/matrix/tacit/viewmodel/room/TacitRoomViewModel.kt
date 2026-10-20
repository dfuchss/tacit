package org.fuchss.matrix.tacit.viewmodel.room

import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.room.RoomViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.RoomViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.settings.OpenAvatarCutterCallback
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OpenMentionCallback
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import org.koin.core.component.get

/** How the room settings pane of a room is presented. */
internal enum class TacitRoomSettingsMode {
    /** Regular room/DM settings with the usual back/close header. */
    ROOM,

    /**
     * Guild or category settings, opened from the room list: no back/close button, and closing them closes the space
     * room as well, since the space itself has no timeline worth returning to.
     */
    SPACE,
}

internal interface TacitRoomViewModel : RoomViewModel {
    val settingsMode: StateFlow<TacitRoomSettingsMode>

    /**
     * Opens [roomId] through upstream's `onOpenRoom` callback (the room list router) once this account has joined it,
     * so a just-created DM is not shown as a join prompt before its first sync.
     */
    suspend fun openRoomWhenJoined(roomId: RoomId)
}

internal object TacitRoomViewModelFactory : RoomViewModelFactory {
    override fun create(
        viewModelContext: MatrixClientViewModelContext,
        selectedRoomId: RoomId,
        onOpenRoom: (UserId, RoomId) -> Unit,
        onCloseRoom: () -> Unit,
        onOpenMention: OpenMentionCallback,
        onOpenAvatarCutter: OpenAvatarCutterCallback,
    ): RoomViewModel {
        val delegate = RoomViewModelFactory.create(
            viewModelContext = viewModelContext,
            selectedRoomId = selectedRoomId,
            onOpenRoom = onOpenRoom,
            onCloseRoom = onCloseRoom,
            onOpenMention = onOpenMention,
            onOpenAvatarCutter = onOpenAvatarCutter,
        )
        return TacitRoomViewModelImpl(delegate, viewModelContext, selectedRoomId, onOpenRoom)
    }
}

private class TacitRoomViewModelImpl(
    private val delegate: RoomViewModel,
    viewModelContext: MatrixClientViewModelContext,
    private val roomId: RoomId,
    private val onOpenRoom: (UserId, RoomId) -> Unit,
) : TacitRoomViewModel, RoomViewModel by delegate, MatrixClientViewModelContext by viewModelContext {

    private val spaceSettings = TacitSpaceSettingsHandOver(
        roomId = roomId,
        requests = get<TacitSpaceSettingsRequests>(),
        scope = coroutineScope,
        openRoomSettings = { delegate.openRoomSettings() },
    )
    override val settingsMode: StateFlow<TacitRoomSettingsMode> = spaceSettings.settingsMode

    override suspend fun openRoomWhenJoined(roomId: RoomId) {
        matrixClient.room.getById(roomId).first { it?.membership == Membership.JOIN }
        onOpenRoom(userId, roomId)
    }
}
