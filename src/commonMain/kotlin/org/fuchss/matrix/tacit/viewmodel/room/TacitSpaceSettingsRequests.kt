package org.fuchss.matrix.tacit.viewmodel.room

import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One-shot hand-over of "open this space in guild/category settings mode" from the room list view model to the
 * room view model of that space.
 *
 * Upstream does such cross-router navigation inside `MainViewModelImpl` (e.g. `onOpenUserProfile` selects the room
 * and then calls the active room view model), which Tacit cannot extend because the routers and their callbacks are
 * private. The room list therefore files a request here and selects the room through the regular upstream path; the
 * [TacitRoomViewModel] of that room consumes it and owns the resulting [TacitRoomSettingsMode].
 *
 * Messenger-scoped (registered in the view-model Koin module), holds at most one pending request: the latest wins.
 */
internal class TacitSpaceSettingsRequests {
    private val pending = MutableStateFlow<RoomId?>(null)

    /** The space room whose settings were requested but not yet opened, if any. */
    val pendingRoomId: StateFlow<RoomId?> = pending.asStateFlow()

    fun request(spaceRoomId: RoomId) {
        pending.value = spaceRoomId
    }

    /** Takes the pending request if it targets [roomId]; returns whether it did. */
    fun consume(roomId: RoomId): Boolean = pending.compareAndSet(roomId, null)

    /**
     * Drops a pending request for [roomId]. Called when that room is reached by another path than a room view model
     * (answering a guild invite), so the request must not fire when the space is opened later.
     */
    fun discard(roomId: RoomId) {
        pending.compareAndSet(roomId, null)
    }

    /**
     * Drops a pending request for any room other than [roomId]. Called when a room is opened: the request belongs to a
     * navigation that has been superseded, so it must not fire later when that space happens to be opened normally.
     */
    fun discardUnlessFor(roomId: RoomId) {
        pending.update { if (it == roomId) it else null }
    }
}
