package org.fuchss.matrix.tacit.viewmodel.space

import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomDisplayName
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class TacitSpaceModelTest {
    private val user = UserId("@tacit:example.org")
    private val guild = RoomId("!guild:example.org")
    private val category = RoomId("!category:example.org")
    private val channel = RoomId("!channel:example.org")
    private val other = RoomId("!other:example.org")

    private fun <C : StateEventContent> state(stateKey: String, content: C) = ClientEvent.RoomEvent.StateEvent(
        content = content,
        id = EventId("\$event-$stateKey"),
        sender = user,
        roomId = guild,
        originTimestamp = 0L,
        stateKey = stateKey,
    )

    private fun space(roomId: RoomId, membership: Membership = Membership.JOIN, name: String? = null) =
        TacitSpaceRoom(roomId, membership, name, avatarUri = null)

    @Test
    fun `space children without via servers are removed children`() {
        val children = parseSpaceChildren(
            listOf(
                state(category.full, ChildEventContent(via = setOf("example.org"))),
                state(channel.full, ChildEventContent(via = emptySet())),
                state(other.full, ChildEventContent(via = setOf("a.org", "b.org"))),
            )
        )

        assertEquals(mapOf(category to setOf("example.org"), other to setOf("a.org", "b.org")), children)
    }

    @Test
    fun `only valid orders of present children are kept`() {
        val nested = RoomId("!nested:example.org")
        val orders = parseSpaceChildOrders(
            listOf(
                state(category.full, ChildEventContent(via = setOf("example.org"), order = "b")),
                state(channel.full, ChildEventContent(via = emptySet(), order = "a")),
                state(other.full, ChildEventContent(via = setOf("example.org"), order = "x".repeat(51))),
                state(guild.full, ChildEventContent(via = setOf("example.org"), order = "ü")),
                state(nested.full, ChildEventContent(via = setOf("example.org"), order = " ~")),
            )
        )

        assertEquals(mapOf(category to "b", nested to " ~"), orders)
        assertEquals("x".repeat(50), validSpaceChildOrder("x".repeat(50)))
        assertNull(validSpaceChildOrder("tab\t"))
    }

    @Test
    fun `the canonical parent is preferred over the first one`() {
        val parents = parseSpaceParents(
            listOf(
                state(category.full, ParentEventContent(canonical = false, via = setOf("example.org"))),
                state(guild.full, ParentEventContent(canonical = true, via = setOf("example.org"))),
            )
        )

        assertEquals(listOf(TacitSpaceParent(category, false), TacitSpaceParent(guild, true)), parents)
        assertEquals(guild, parents.preferredParent())
        assertEquals(category, parents.take(1).preferredParent())
        assertNull(emptyList<TacitSpaceParent>().preferredParent())
    }

    @Test
    fun `the root space is the topmost preferred parent`() = runTest {
        val parentOf = mapOf(channel to category, category to guild)

        assertEquals(guild, findRootSpace(category) { parentOf[it] })
        assertEquals(guild, findRootSpace(guild) { parentOf[it] })
        assertNull(findRootSpace(null) { parentOf[it] })
    }

    @Test
    fun `a parent cycle ends where it closes`() = runTest {
        val parentOf = mapOf(category to guild, guild to category)

        assertEquals(category, findRootSpace(category) { parentOf[it] })
    }

    @Test
    fun `only joined and invited spaces are spaces of the graph`() {
        fun room(membership: Membership, type: RoomType? = RoomType.Space) = Room(
            roomId = guild,
            createEventContent = type?.let { CreateEventContent(type = it) },
            membership = membership,
            name = RoomDisplayName(explicitName = "Guild", heroes = emptyList(), otherUsersCount = 0, isEmpty = false, summary = null),
            avatarUrl = "",
        )

        assertEquals(space(guild, name = "Guild"), room(Membership.JOIN).toSpaceRoomOrNull())
        assertEquals(space(guild, Membership.INVITE, "Guild"), room(Membership.INVITE).toSpaceRoomOrNull())
        assertNull(room(Membership.LEAVE).toSpaceRoomOrNull())
        assertNull(room(Membership.JOIN, type = null).toSpaceRoomOrNull())
    }

    @Test
    fun `joined child spaces are categories, not guilds`() {
        val graph = TacitSpaceGraph(
            spaces = listOf(space(guild, name = "Zeta"), space(category, name = "Category"), space(other, name = "Alpha")),
            childrenBySpace = mapOf(guild to setOf(category, channel), category to setOf(channel), other to emptySet()),
        )

        val guilds = graph.topLevelGuilds(user)

        assertEquals(listOf(other, guild), guilds.map { it.roomId })
        assertEquals(listOf("Alpha", "Zeta"), guilds.map { it.displayName })
        assertEquals(setOf(user), guilds.map { it.userId }.toSet())
    }

    @Test
    fun `an invited child space still shows up as a guild`() {
        val graph = TacitSpaceGraph(
            spaces = listOf(space(guild), space(category, Membership.INVITE)),
            childrenBySpace = mapOf(guild to setOf(category)),
        )

        assertEquals(listOf(category, guild), graph.topLevelGuilds(user).map { it.roomId })
    }

    private fun loopGraph(vararg links: Pair<RoomId, Set<RoomId>>): TacitSpaceGraph {
        val childrenBySpace = links.toMap()
        return TacitSpaceGraph(childrenBySpace.keys.map { space(it, name = it.full) }, childrenBySpace)
    }

    @Test
    fun `members of a two-space loop are guilds marked as in a loop`() {
        val guilds = loopGraph(guild to setOf(category), category to setOf(guild)).topLevelGuilds(user)

        assertEquals(setOf(guild, category), guilds.map { it.roomId }.toSet())
        assertTrue(guilds.all { it.isInLoop })
    }

    @Test
    fun `members of a longer loop are guilds, their other descendants stay nested`() {
        val nested = RoomId("!nested:example.org")
        val graph = loopGraph(guild to setOf(category), category to setOf(other, channel), other to setOf(guild), channel to setOf(nested), nested to emptySet())

        val guilds = graph.topLevelGuilds(user)

        assertEquals(setOf(guild, category, other), guilds.map { it.roomId }.toSet())
        assertTrue(guilds.all { it.isInLoop })
        assertEquals(setOf(guild, category, other, channel, nested), guilds.flatMap { graph.descendantsOf(it.roomId) }.toSet())
    }

    @Test
    fun `a loop below a normal guild promotes only its members`() {
        val nested = RoomId("!nested:example.org")
        val graph = loopGraph(guild to setOf(category), category to setOf(other), other to setOf(category, channel), channel to setOf(nested), nested to emptySet())

        val guilds = graph.topLevelGuilds(user).associate { it.roomId to it.isInLoop }

        assertEquals(mapOf(guild to false, category to true, other to true), guilds)
    }

    @Test
    fun `a space listing itself stays a guild marked as in a loop`() {
        val guilds = loopGraph(guild to setOf(guild, channel)).topLevelGuilds(user)

        assertEquals(listOf(guild), guilds.map { it.roomId })
        assertTrue(guilds.single().isInLoop)
    }

    @Test
    fun `guilds without loops are not marked`() {
        val guilds = loopGraph(guild to setOf(category), category to setOf(channel)).topLevelGuilds(user)

        assertEquals(listOf(guild), guilds.map { it.roomId })
        assertFalse(guilds.single().isInLoop)
    }

    @Test
    fun `invited loop members show up once`() {
        val graph = TacitSpaceGraph(
            spaces = listOf(space(guild), space(category, Membership.INVITE)),
            childrenBySpace = mapOf(guild to setOf(category), category to setOf(guild)),
        )

        assertEquals(listOf(category, guild), graph.topLevelGuilds(user).map { it.roomId })
    }

    @Test
    fun `descendants include categories and their channels at any depth`() {
        val nested = RoomId("!nested:example.org")
        val graph = TacitSpaceGraph(
            spaces = emptyList(),
            childrenBySpace = mapOf(guild to setOf(category, other), category to setOf(channel), channel to setOf(nested)),
        )

        assertEquals(setOf(category, other, channel, nested), graph.descendantsOf(guild))
        assertEquals(setOf(channel, nested), graph.descendantsOf(category))
        assertEquals(emptySet(), graph.descendantsOf(nested))
    }

    @Test
    fun `descendants terminate on cycles`() {
        val graph = TacitSpaceGraph(emptyList(), mapOf(guild to setOf(category), category to setOf(guild)))

        assertEquals(setOf(category, guild), graph.descendantsOf(guild))
    }

    @Test
    fun `pages are followed until there is no next batch`() = runTest {
        val requested = mutableListOf<String?>()
        val pages = mapOf(null to TacitPage(listOf(1, 2), "b"), "b" to TacitPage(listOf(3), "c"), "c" to TacitPage(listOf(4), null))

        val items = collectPages { from -> requested += from; Result.success(pages.getValue(from)) }

        assertEquals(listOf(1, 2, 3, 4), items)
        assertEquals(listOf(null, "b", "c"), requested)
    }

    @Test
    fun `a failing page keeps the pages before it`() = runTest {
        val items = collectPages { from ->
            if (from == null) Result.success(TacitPage(listOf(1), "b")) else Result.failure(IllegalStateException())
        }

        assertEquals(listOf(1), items)
        assertEquals(emptyList(), collectPages<Int> { Result.failure(IllegalStateException()) })
    }

    @Test
    fun `pagination stops on a repeated batch token and after the page limit`() = runTest {
        var calls = 0
        val repeated = collectPages { calls++; Result.success(TacitPage(listOf(calls), "same")) }
        assertEquals(listOf(1, 2), repeated)

        var endless = 0
        val limited = collectPages(maxPages = 3) { endless++; Result.success(TacitPage(listOf(endless), "next-$endless")) }
        assertEquals(listOf(1, 2, 3), limited)
    }
}
