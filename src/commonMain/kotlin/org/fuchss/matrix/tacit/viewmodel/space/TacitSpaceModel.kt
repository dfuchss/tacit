package org.fuchss.matrix.tacit.viewmodel.space

import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry

/*
 * Pure parsing and reducer functions behind [TacitSpaceHierarchy]. Tacit models Matrix spaces as
 * guild (top-level space) → categories (child spaces) → channels (child rooms).
 */

/** A joined or invited space, reduced to what the guild rail needs. */
internal data class TacitSpaceRoom(
    val roomId: RoomId,
    val membership: Membership,
    val name: String?,
    val avatarUri: String?,
)

/** All joined or invited spaces of one account and the rooms each one lists as `m.space.child`. */
internal data class TacitSpaceGraph(
    val spaces: List<TacitSpaceRoom>,
    val childrenBySpace: Map<RoomId, Set<RoomId>>,
) {
    companion object {
        val EMPTY = TacitSpaceGraph(emptyList(), emptyMap())
    }
}

/** One guild's direct children and the categories among them. */
internal data class TacitGuildLayout(
    /** Every direct child of the guild, with the servers to join it via. */
    val directChildren: Map<RoomId, Set<String>>,
    /** The direct children that are spaces (categories), each with its own child room ids. */
    val categories: Map<RoomId, Set<RoomId>>,
    /** Explicit, non-blank names of the categories that the local store knows. */
    val categoryNames: Map<RoomId, String>,
) {
    companion object {
        val EMPTY = TacitGuildLayout(emptyMap(), emptyMap(), emptyMap())
    }
}

/** An `m.space.parent` link of a room. */
internal data class TacitSpaceParent(val roomId: RoomId, val canonical: Boolean)

internal val Room.isSpace: Boolean get() = createEventContent?.type == RoomType.Space

/** The space this room is, if it is a space the account joined or was invited to. */
internal fun Room.toSpaceRoomOrNull(): TacitSpaceRoom? =
    if (isSpace && (membership == Membership.JOIN || membership == Membership.INVITE)) {
        TacitSpaceRoom(roomId, membership, name?.explicitName, avatarUrl?.ifBlank { null })
    } else {
        null
    }

private fun roomIdOrNull(stateKey: String): RoomId? = runCatching { RoomId(stateKey) }.getOrNull()

/**
 * The children listed by a space's `m.space.child` state, with their `via` servers. A child event without `via`
 * servers does not count: that is how a child is removed (and how Tacit unlinks a room from a category).
 */
internal fun parseSpaceChildren(events: Iterable<ClientEvent.StateBaseEvent<ChildEventContent>>): Map<RoomId, Set<String>> =
    events.mapNotNull { event ->
        if (event.content.via.isEmpty()) return@mapNotNull null
        val childRoomId = roomIdOrNull(event.stateKey) ?: return@mapNotNull null
        childRoomId to event.content.via
    }.toMap()

/** The spaces a room names as its parents in its `m.space.parent` state. */
internal fun parseSpaceParents(events: Iterable<ClientEvent.StateBaseEvent<ParentEventContent>>): List<TacitSpaceParent> =
    events.mapNotNull { event ->
        val parentRoomId = roomIdOrNull(event.stateKey) ?: return@mapNotNull null
        TacitSpaceParent(parentRoomId, event.content.canonical)
    }

/** The canonical parent, or else the first one. */
internal fun List<TacitSpaceParent>.preferredParent(): RoomId? =
    (firstOrNull { it.canonical } ?: firstOrNull())?.roomId

/**
 * Follows the preferred parents from [start] up to the topmost space. Returns `null` without a start, and the room
 * where a parent cycle closes if there is one.
 */
internal suspend fun findRootSpace(start: RoomId?, preferredParentOf: suspend (RoomId) -> RoomId?): RoomId? {
    var current = start
    val visited = mutableSetOf<RoomId>()
    while (current != null && visited.add(current)) {
        current = preferredParentOf(current) ?: return current
    }
    return current
}

/**
 * The spaces shown as guilds: all of them except joined spaces that another space lists as a child (those are
 * categories). Invited spaces always show up, so that the invite can be answered. Sorted by name.
 */
internal fun TacitSpaceGraph.topLevelGuilds(userId: UserId): List<GuildEntry> {
    val childRoomIds = childrenBySpace.values.flatten().toSet()
    return spaces
        .filterNot { space -> space.membership == Membership.JOIN && space.roomId in childRoomIds }
        .map { space ->
            GuildEntry(
                roomId = space.roomId,
                userId = userId,
                displayName = space.name,
                avatarUri = space.avatarUri,
                membership = space.membership,
            )
        }
        .sortedBy { it.displayName ?: it.roomId.full }
}

/** Every room below [root] at any depth (categories and their channels), excluding [root] unless in a cycle. */
internal fun TacitSpaceGraph.descendantsOf(root: RoomId): Set<RoomId> {
    val result = mutableSetOf<RoomId>()
    val queue = ArrayDeque<RoomId>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        childrenBySpace[queue.removeFirst()].orEmpty().forEach { child ->
            if (result.add(child)) queue.add(child)
        }
    }
    return result
}

/** One page of a paginated server response. */
internal data class TacitPage<T>(val items: List<T>, val nextBatch: String?)

/**
 * Collects the pages of a paginated endpoint, following `next_batch` for at most [maxPages] pages. Stops at the
 * first failing page and keeps what it already has.
 */
internal suspend fun <T> collectPages(
    maxPages: Int = 50,
    fetchPage: suspend (from: String?) -> Result<TacitPage<T>>,
): List<T> {
    val items = mutableListOf<T>()
    val seenBatches = mutableSetOf<String>()
    var from: String? = null
    repeat(maxPages) {
        val page = fetchPage(from).getOrNull() ?: return items
        items += page.items
        from = page.nextBatch?.takeIf { seenBatches.add(it) } ?: return items
    }
    return items
}
