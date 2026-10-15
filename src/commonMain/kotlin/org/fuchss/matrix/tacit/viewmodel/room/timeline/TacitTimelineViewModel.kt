package org.fuchss.matrix.tacit.viewmodel.room.timeline

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.fuchss.matrix.tacit.viewmodel.room.timeline.entry.ChannelMemberEntry

internal interface TacitTimelineViewModel : TimelineViewModel {
    val roomMembers: StateFlow<List<ChannelMemberEntry>>
    val typingMembers: StateFlow<List<ChannelMemberEntry>>
    val typingIndicatorText: StateFlow<String?>
    val scrollToEndRequests: Flow<Unit>

    /**
     * Reports whether the user currently sees the newest content of the room (timeline scrolled to the
     * end, window focused). While true, the own read receipt is kept on the room's last event.
     */
    fun setCaughtUp(caughtUp: Boolean)

    suspend fun findExistingDirectMessageRoom(userId: UserId): RoomId?
    suspend fun findOrCreateDirectMessageRoom(userId: UserId): RoomId?
}
