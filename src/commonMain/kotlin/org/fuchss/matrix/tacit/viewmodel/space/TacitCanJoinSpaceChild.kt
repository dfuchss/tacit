package org.fuchss.matrix.tacit.viewmodel.space

import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.JoinRulesEventContent.JoinRule
import de.connect2x.trixnity.core.model.events.m.room.Membership

/*
 * Copied from Trixnity Messenger v4.11.0, viewmodel/spaces/canJoinSpaceChild.kt (internal there). Replace with an
 * import once Trixnity Messenger makes `canJoinSpaceChild` public.
 */

/**
 * Whether the account can join a space child with the given [membership], join [rule] and [roomType]:
 * [allowedByRoomMembership] tells whether it is a member of one of the rooms a restricted join rule allows.
 */
internal fun canJoinSpaceChild(
    membership: Membership?,
    rule: JoinRule?,
    roomType: RoomType?,
    allowedByRoomMembership: Boolean,
): Boolean {
    val supported = roomType == null || roomType == RoomType.Room || roomType == RoomType.Space

    return supported &&
        membership !in setOf(Membership.JOIN, Membership.BAN) &&
        (membership == Membership.INVITE ||
            rule == JoinRule.Public ||
            (rule in setOf(JoinRule.Restricted, JoinRule.KnockRestricted) && allowedByRoomMembership))
}
