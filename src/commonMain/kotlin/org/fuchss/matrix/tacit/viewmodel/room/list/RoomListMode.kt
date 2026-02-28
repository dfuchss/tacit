package org.fuchss.matrix.tacit.viewmodel.room.list

import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry

internal sealed interface RoomListMode {
    data object DirectMessages : RoomListMode
    data class GuildChannels(val guild: GuildEntry) : RoomListMode
}

internal val RoomListMode.selectedGuildOrNull: GuildEntry?
    get() = when (this) {
        is RoomListMode.DirectMessages -> null
        is RoomListMode.GuildChannels -> guild
    }

internal fun RoomListMode.isDirectMessages(): Boolean = this is RoomListMode.DirectMessages

internal fun RoomListMode.roomsSectionTitle(): String = when (this) {
    is RoomListMode.DirectMessages -> "CHATS & ROOMS"
    is RoomListMode.GuildChannels -> "ROOMS"
}
