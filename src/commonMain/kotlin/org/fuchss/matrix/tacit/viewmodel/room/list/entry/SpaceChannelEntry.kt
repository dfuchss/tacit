package org.fuchss.matrix.tacit.viewmodel.room.list.entry

import de.connect2x.trixnity.core.model.RoomId

internal enum class SpaceChannelStatus {
    JOINED,
    INVITED,
    KNOCKING,
    LEFT,
    NOT_JOINED,
    UNKNOWN,
}

internal data class SpaceChannelEntry(
    val roomId: RoomId,
    val displayName: String,
    val status: SpaceChannelStatus,
    val isJoined: Boolean,
    val isJoinable: Boolean,
    val via: Set<String>,
)
