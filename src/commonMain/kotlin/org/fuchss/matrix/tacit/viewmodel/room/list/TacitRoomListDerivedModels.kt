package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry

internal data class TacitRoomDerived(
    val room: TacitRoomListElementViewModel,
    val roomId: RoomId,
    val roomName: String?,
    val isDirect: Boolean,
    val isSpace: Boolean,
    val isInvite: Boolean,
    val isKnock: Boolean,
    val isLeave: Boolean,
    val isJoined: Boolean,
    val isUnread: Boolean,
)

internal data class TacitUnknownDisplayNamesContext(
    val guild: GuildEntry?,
    val client: MatrixClient?,
    val children: Map<RoomId, Set<String>>,
    val rooms: List<TacitRoomDerived>,
)
