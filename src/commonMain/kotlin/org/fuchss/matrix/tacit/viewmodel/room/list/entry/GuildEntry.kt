package org.fuchss.matrix.tacit.viewmodel.room.list.entry

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership

internal data class GuildEntry(
    val roomId: RoomId,
    val userId: UserId,
    val displayName: String?,
    val avatarUri: String?,
    val membership: Membership,
) {
    val isInvite: Boolean get() = membership == Membership.INVITE

    fun guildLabel(): String {
        val source = displayName?.ifBlank { null } ?: roomId.full.removePrefix("!").substringBefore(":")
        val parts = source.split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "T"
            parts.size == 1 -> parts.first().take(3).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    fun key(): String = "${userId.full}:${roomId.full}"
}
