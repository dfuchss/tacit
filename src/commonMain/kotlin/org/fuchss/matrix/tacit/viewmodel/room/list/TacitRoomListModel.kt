package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelStatus
import org.fuchss.matrix.tacit.viewmodel.space.TacitGuildLayout
import org.fuchss.matrix.tacit.viewmodel.space.TacitHierarchyRoom
import org.fuchss.matrix.tacit.viewmodel.space.canJoinSpaceChild

/*
 * Pure reducers behind the room list: they turn the rooms' state and the space hierarchy into what the guild rail,
 * the channel list and the browse dialog show. Generic in the room handle [R] so that tests need no view models.
 */

/** A room of the room list with its state flattened from the element view model's flows. */
internal data class TacitRoomDerived<out R>(
    val room: R,
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

/** A category of the selected guild with its joined channels, in room-list order. */
internal data class TacitChannelGroup<out R>(
    val categoryRoomId: RoomId,
    val categoryName: String,
    val channels: List<R>,
)

internal typealias TacitGuildChannelGroup = TacitChannelGroup<TacitRoomListElementViewModel>

/** DMs, and group chats that no guild places at any depth: the DM home lists both, so no room is unreachable. */
internal fun TacitRoomDerived<*>.belongsToDirectMessages(placedRoomIds: Set<RoomId>): Boolean =
    isDirect || (!isSpace && roomId !in placedRoomIds)

internal fun <R> directMessageRooms(rooms: List<TacitRoomDerived<R>>, placedRoomIds: Set<RoomId>) =
    rooms.filter { it.isJoined && it.belongsToDirectMessages(placedRoomIds) }

internal fun <R> guildRooms(rooms: List<TacitRoomDerived<R>>, guildRoomIds: Set<RoomId>) =
    rooms.filter { it.isJoined && !it.isDirect && !it.isSpace && it.roomId in guildRoomIds }

internal fun <R> directMessageInvites(rooms: List<TacitRoomDerived<R>>, placedRoomIds: Set<RoomId>) =
    rooms.filter { it.isInvite && it.belongsToDirectMessages(placedRoomIds) }

/** Invites to the guild itself, to its direct children and to the channels of its categories. */
internal fun <R> guildInvites(rooms: List<TacitRoomDerived<R>>, guildRoomId: RoomId, layout: TacitGuildLayout): List<TacitRoomDerived<R>> {
    val categoryChannels = layout.categories.values.flatten().toSet()
    return rooms.filter {
        it.isInvite && !it.isDirect &&
                (it.roomId == guildRoomId || it.roomId in layout.directChildren || it.roomId in categoryChannels)
    }
}

internal fun directMessagesUnreadCount(rooms: List<TacitRoomDerived<*>>, placedRoomIds: Set<RoomId>): Int =
    rooms.count { it.isUnread && it.isJoined && it.belongsToDirectMessages(placedRoomIds) }

/** Unread joined channels per guild key, counted over every room the guild places at any depth. */
internal fun guildUnreadCounts(
    rooms: List<TacitRoomDerived<*>>,
    guilds: List<GuildEntry>,
    roomIdsByGuild: Map<String, Set<RoomId>>,
): Map<String, Int> {
    val unread = rooms.filter { !it.isDirect && it.isJoined && it.isUnread }.map { it.roomId }.toSet()
    return guilds.associate { guild -> guild.key() to roomIdsByGuild[guild.key()].orEmpty().count { it in unread } }
}

/** Guilds in the user's [order] (guild keys) first, then the remaining ones by name. */
internal fun orderGuilds(guilds: List<GuildEntry>, order: List<String>): List<GuildEntry> {
    val byKey = guilds.associateBy { it.key() }
    val orderedKeys = order.filter { it in byKey }
    val unordered = guilds.filterNot { it.key() in orderedKeys }.sortedBy { it.displayName ?: it.roomId.full }
    return orderedKeys.mapNotNull { byKey[it] } + unordered
}

/**
 * The selected guild's categories with their visible channels. Categories without a visible channel are hidden.
 * Ordered by the user's [categoryOrder] (category room ids), then by name.
 */
internal fun <R> guildChannelGroups(
    rooms: List<TacitRoomDerived<R>>,
    visibleRooms: List<TacitRoomDerived<R>>,
    layout: TacitGuildLayout,
    hierarchyRooms: Map<RoomId, TacitHierarchyRoom>,
    categoryOrder: List<String>,
): List<TacitChannelGroup<R>> {
    val roomById = rooms.associateBy { it.roomId }
    val visibleOrder = visibleRooms.withIndex().associate { (index, room) -> room.roomId to index }
    val visibleById = visibleRooms.associateBy { it.roomId }
    fun displayName(roomId: RoomId): String {
        val derivedName = roomById[roomId]?.roomName
        return layout.categoryNames[roomId]?.takeIf { it.isNotBlank() }
            ?: derivedName?.takeIf { it.isNotBlank() && it != roomId.full }
            ?: hierarchyRooms[roomId]?.name?.takeIf { it.isNotBlank() }
            ?: derivedName
            ?: roomId.full
    }

    return layout.categories.keys
        .filter { roomById[it]?.isSpace != false }
        .sortedWith(
            compareBy<RoomId> { categoryOrder.indexOf(it.full).let { index -> if (index >= 0) index else Int.MAX_VALUE } }
                .thenBy { displayName(it).lowercase() }
        )
        .mapNotNull { categoryRoomId ->
            val channels = layout.categories[categoryRoomId].orEmpty()
                .mapNotNull { visibleById[it] }
                .sortedBy { visibleOrder[it.roomId] ?: Int.MAX_VALUE }
                .map { it.room }
            if (channels.isEmpty()) null
            else TacitChannelGroup(categoryRoomId, displayName(categoryRoomId), channels)
        }
}

/** Rooms the browse dialog considers: the guild's children, its categories' children and the server hierarchy. */
internal fun browseCandidates(layout: TacitGuildLayout, hierarchyRooms: Map<RoomId, TacitHierarchyRoom>): Set<RoomId> =
    layout.directChildren.keys + layout.categories.values.flatten() + hierarchyRooms.keys

/**
 * The browse dialog's channels: joined, joinable, not joinable, invited or knocking candidates, sorted by name. Rooms
 * the room list knows report their own state; others fall back to their [memberships] in the store. Whether a room can
 * be joined follows its join rule in the server hierarchy ([canJoinSpaceChild]); a `restricted` room is joinable if
 * the account is a member of one of its allowed rooms, which [memberships] should therefore also cover.
 */
internal fun browseChannelEntries(
    rooms: List<TacitRoomDerived<*>>,
    candidates: Set<RoomId>,
    layout: TacitGuildLayout,
    hierarchyRooms: Map<RoomId, TacitHierarchyRoom>,
    memberships: Map<RoomId, Membership?>,
): List<SpaceChannelEntry> {
    val known = rooms.filter { !it.isDirect && it.roomId in candidates }.associateBy { it.roomId }
    val joinedRoomIds = rooms.filter { it.isJoined }.map { it.roomId }.toSet() +
            memberships.filterValues { it == Membership.JOIN }.keys

    fun entry(roomId: RoomId, displayName: String, membership: Membership?): SpaceChannelEntry {
        val hierarchyRoom = hierarchyRooms[roomId]
        val canJoin = canJoinSpaceChild(
            membership = membership,
            rule = hierarchyRoom?.joinRule,
            roomType = hierarchyRoom?.roomType,
            allowedByRoomMembership = hierarchyRoom?.allowedRoomIds.orEmpty().any { it in joinedRoomIds },
        )
        val status = when {
            membership == Membership.JOIN -> SpaceChannelStatus.JOINED
            membership == Membership.INVITE -> SpaceChannelStatus.INVITED
            membership == Membership.KNOCK -> SpaceChannelStatus.KNOCKING
            canJoin && membership == Membership.LEAVE -> SpaceChannelStatus.LEFT
            canJoin -> SpaceChannelStatus.NOT_JOINED
            hierarchyRoom != null || membership == Membership.BAN -> SpaceChannelStatus.NOT_JOINABLE
            else -> SpaceChannelStatus.UNKNOWN
        }
        return SpaceChannelEntry(
            roomId = roomId,
            displayName = displayName,
            status = status,
            isJoined = status == SpaceChannelStatus.JOINED,
            // Invites are answered from the room list, not joined from here.
            isJoinable = status == SpaceChannelStatus.NOT_JOINED || status == SpaceChannelStatus.LEFT,
            via = layout.directChildren[roomId].orEmpty(),
        )
    }

    val knownEntries = known.values.map { room ->
        val membership = when {
            room.isJoined -> Membership.JOIN
            room.isInvite -> Membership.INVITE
            room.isKnock -> Membership.KNOCK
            room.isLeave -> Membership.LEAVE
            else -> memberships[room.roomId]
        }
        entry(room.roomId, room.roomName ?: hierarchyRooms[room.roomId]?.name ?: room.roomId.full, membership)
    }

    val unknownEntries = candidates.filterNot { it in known }.map { roomId ->
        entry(roomId, hierarchyRooms[roomId]?.name ?: roomId.full, memberships[roomId])
    }

    return (knownEntries + unknownEntries)
        .filter { it.status != SpaceChannelStatus.UNKNOWN }
        .sortedBy { it.displayName.lowercase() }
}
