package org.fuchss.matrix.tacit.viewmodel.room.list.entry

import de.connect2x.trixnity.core.model.RoomId

internal data class SpaceChannelEntry(
    val roomId: RoomId,
    val displayName: String,
    val status: String,
    val isJoined: Boolean,
    val isJoinable: Boolean,
    val via: Set<String>,
)
