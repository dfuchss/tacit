package org.fuchss.matrix.tacit.viewmodel.util

import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.Membership
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectMessageCandidatesTest {
    private fun room(
        id: String,
        isDirect: Boolean = true,
        membership: Membership = Membership.JOIN,
        type: RoomType? = null,
    ) = Room(
        roomId = RoomId("!$id:example.org"),
        createEventContent = type?.let { CreateEventContent(type = it) },
        isDirect = isDirect,
        membership = membership,
    )

    @Test
    fun onlyJoinedDirectNonSpaceRoomsAreCandidates() {
        val dm = room("dm")
        val rooms = listOf(
            dm,
            room("group", isDirect = false),
            room("invited", membership = Membership.INVITE),
            room("left", membership = Membership.LEAVE),
            room("space", type = RoomType.Space),
        )

        assertEquals(listOf(dm.roomId), rooms.directMessageCandidates().map { it.roomId })
    }
}
