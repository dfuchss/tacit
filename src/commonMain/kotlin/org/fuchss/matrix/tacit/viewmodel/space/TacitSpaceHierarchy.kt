package org.fuchss.matrix.tacit.viewmodel.space

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/**
 * Reads Tacit's guild → category → channel model from Matrix space state (`m.space.child`, `m.space.parent` and the
 * server's space hierarchy). The single place that parses space relations; the room list and the room settings
 * only consume its flows. Stateless: callers own the subscriptions (usually via `stateIn`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TacitSpaceHierarchy {

    /** The `m.space.child` children of [spaceRoomId] with their `via` servers. */
    fun children(client: MatrixClient, spaceRoomId: RoomId): Flow<Map<RoomId, Set<String>>> =
        client.room.getAllState(spaceRoomId, ChildEventContent::class)
            .flattenValues()
            .map(::parseSpaceChildren)
            .distinctUntilChanged()

    /** The `m.space.parent` links of [roomId]. */
    fun parents(client: MatrixClient, roomId: RoomId): Flow<List<TacitSpaceParent>> =
        client.room.getAllState(roomId, ParentEventContent::class)
            .flattenValues()
            .map(::parseSpaceParents)
            .distinctUntilChanged()

    /**
     * All joined or invited spaces of [client] and their children. Child subscriptions are only rebuilt when the set
     * of spaces changes, not on every sync that touches some room.
     */
    fun spaceGraph(client: MatrixClient): Flow<TacitSpaceGraph> = channelFlow {
        val spaces = client.room.getAll()
            .flattenValues()
            .map { rooms -> rooms.mapNotNull { it.toSpaceRoomOrNull() }.sortedBy { it.roomId.full } }
            .distinctUntilChanged()
            .shareIn(this, SharingStarted.Eagerly, replay = 1)
        val childrenBySpace = spaces
            .map { list -> list.map { it.roomId } }
            .distinctUntilChanged()
            .flatMapLatest { spaceRoomIds ->
                if (spaceRoomIds.isEmpty()) flowOf(emptyMap())
                else combine(spaceRoomIds.map { id -> children(client, id).map { id to it.keys } }) { it.toMap() }
            }
        combine(spaces, childrenBySpace, ::TacitSpaceGraph).collect { send(it) }
    }.distinctUntilChanged()

    /** [spaceGraph] of every client, keyed by account. */
    fun spaceGraphs(clients: List<MatrixClient>): Flow<Map<UserId, TacitSpaceGraph>> =
        if (clients.isEmpty()) flowOf(emptyMap())
        else combine(clients.map { client -> spaceGraph(client).map { client.userId to it } }) { it.toMap() }

    /** The direct children of [guildRoomId] and, for those that are spaces, their children and names. */
    fun guildLayout(client: MatrixClient, guildRoomId: RoomId): Flow<TacitGuildLayout> =
        children(client, guildRoomId)
            .flatMapLatest { directChildren ->
                if (directChildren.isEmpty()) flowOf(TacitGuildLayout(directChildren, emptyMap(), emptyMap()))
                else combine(directChildren.keys.map { category(client, it) }) { categories ->
                    val spaces = categories.filterNotNull()
                    TacitGuildLayout(
                        directChildren = directChildren,
                        categories = spaces.associate { it.roomId to it.children },
                        categoryNames = spaces.mapNotNull { c -> c.name?.let { c.roomId to it } }.toMap(),
                    )
                }
            }
            .distinctUntilChanged()

    private data class CategoryName(val name: String?)
    private data class Category(val roomId: RoomId, val name: String?, val children: Set<RoomId>)

    /** [roomId] as a category, or `null` while the local store does not know it as a space. */
    private fun category(client: MatrixClient, roomId: RoomId): Flow<Category?> =
        client.room.getById(roomId)
            .map { room -> room?.takeIf { it.isSpace }?.let { CategoryName(it.name?.explicitName?.takeIf(String::isNotBlank)) } }
            .distinctUntilChanged()
            .flatMapLatest { category ->
                if (category == null) flowOf(null)
                else children(client, roomId).map { Category(roomId, category.name, it.keys) }
            }

    /** The guild (top-level space) [roomId] belongs to, following canonical parents; `null` if it has no parent. */
    fun guildOf(client: MatrixClient, roomId: RoomId): Flow<RoomId?> =
        parents(client, roomId)
            .mapLatest { parents ->
                findRootSpace(parents.preferredParent()) { current -> parents(client, current).first().preferredParent() }
            }
            .distinctUntilChanged()

    /**
     * Names of all rooms in the server-side hierarchy below [rootRoomId], following pagination. Includes rooms the
     * account has not joined. Empty if the server cannot be asked.
     */
    suspend fun hierarchyNames(client: MatrixClient, rootRoomId: RoomId): Map<RoomId, String> =
        collectPages { from ->
            client.api.room.getHierarchy(roomId = rootRoomId, from = from, limit = HIERARCHY_PAGE_SIZE)
                .map { response -> TacitPage(response.rooms, response.nextBatch) }
        }.associate { room -> room.roomId to (room.name?.takeIf { it.isNotBlank() } ?: room.roomId.full) }

    private companion object {
        const val HIERARCHY_PAGE_SIZE = 200L
    }
}
