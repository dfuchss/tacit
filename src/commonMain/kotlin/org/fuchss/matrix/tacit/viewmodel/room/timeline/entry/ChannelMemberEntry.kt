package org.fuchss.matrix.tacit.viewmodel.room.timeline.entry

import de.connect2x.trixnity.core.model.UserId

internal data class ChannelMemberEntry(
    val userId: UserId,
    val displayName: String,
    val isSelf: Boolean,
    val avatarImage: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ChannelMemberEntry

        if (isSelf != other.isSelf) return false
        if (userId != other.userId) return false
        if (displayName != other.displayName) return false
        if (!avatarImage.contentEquals(other.avatarImage)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isSelf.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + (avatarImage?.contentHashCode() ?: 0)
        return result
    }
}
