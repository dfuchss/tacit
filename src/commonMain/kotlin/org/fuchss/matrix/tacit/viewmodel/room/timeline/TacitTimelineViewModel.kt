package org.fuchss.matrix.tacit.viewmodel.room.timeline

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import kotlinx.coroutines.flow.StateFlow
import org.fuchss.matrix.tacit.viewmodel.room.timeline.entry.ChannelMemberEntry

internal interface TacitTimelineViewModel : TimelineViewModel {
    val roomMembers: StateFlow<List<ChannelMemberEntry>>
    val typingMembers: StateFlow<List<ChannelMemberEntry>>
    val typingIndicatorText: StateFlow<String?>

    suspend fun findExistingDirectMessageRoom(userId: UserId): RoomId?
    suspend fun findOrCreateDirectMessageRoom(userId: UserId): RoomId?
}
