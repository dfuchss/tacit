package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.core.model.RoomId

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

internal data class CategoryHierarchyNames(
    val children: Map<RoomId, Set<String>>,
    val categoryChildren: Map<RoomId, Set<RoomId>>,
    val directSpaceNames: Map<RoomId, String>,
    val hierarchyNames: Map<RoomId, String>,
)
