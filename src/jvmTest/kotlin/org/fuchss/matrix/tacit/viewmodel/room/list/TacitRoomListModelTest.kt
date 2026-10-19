package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelStatus
import org.fuchss.matrix.tacit.viewmodel.space.TacitGuildLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class TacitRoomListModelTest {
    private fun id(name: String) = RoomId("!$name:example.org")

    private fun room(
        name: String,
        roomName: String? = name,
        isDirect: Boolean = false,
        isSpace: Boolean = false,
        isInvite: Boolean = false,
        isKnock: Boolean = false,
        isLeave: Boolean = false,
        isJoined: Boolean = !isInvite && !isKnock && !isLeave,
        isUnread: Boolean = false,
    ) = TacitRoomDerived(name, id(name), roomName, isDirect, isSpace, isInvite, isKnock, isLeave, isJoined, isUnread)

    private fun guild(name: String, displayName: String? = name) =
        GuildEntry(id(name), UserId("@tacit:example.org"), displayName, avatarUri = null, membership = Membership.JOIN)

    private val guild = id("guild")
    private val lobby = id("lobby")
    private val games = id("games")

    @Test
    fun `the DM home lists DMs and group chats that no guild places`() {
        val rooms = listOf(
            room("dm", isDirect = true),
            room("group"),
            room("channel"),
            room("space", isSpace = true),
            room("invited-dm", isDirect = true, isInvite = true),
            room("invited-group", isInvite = true),
            room("invited-channel", isInvite = true),
        )
        val placed = setOf(id("channel"), id("invited-channel"))

        assertEquals(listOf("dm", "group"), directMessageRooms(rooms, placed).map { it.room })
        assertEquals(listOf("invited-dm", "invited-group"), directMessageInvites(rooms, placed).map { it.room })
    }

    @Test
    fun `a guild lists its joined channels, but no DMs or spaces`() {
        val rooms = listOf(room("a"), room("dm", isDirect = true), room("category", isSpace = true), room("left", isLeave = true), room("b"))
        val guildRoomIds = setOf(id("a"), id("dm"), id("category"), id("left"), id("b"))

        assertEquals(listOf("a", "b"), guildRooms(rooms, guildRoomIds).map { it.room })
    }

    @Test
    fun `guild invites cover the guild, its children and its categories' channels`() {
        val layout = TacitGuildLayout(
            directChildren = mapOf(lobby to setOf("example.org"), id("direct") to setOf("example.org")),
            categories = mapOf(lobby to setOf(id("nested"))),
            categoryNames = emptyMap(),
        )
        val rooms = listOf(
            room("guild", isInvite = true),
            room("direct", isInvite = true),
            room("nested", isInvite = true),
            room("elsewhere", isInvite = true),
            room("dm", isDirect = true, isInvite = true),
            room("joined"),
        )

        assertEquals(listOf("guild", "direct", "nested"), guildInvites(rooms, guild, layout).map { it.room })
    }

    @Test
    fun `unread counts cover joined rooms only`() {
        val rooms = listOf(
            room("dm", isDirect = true, isUnread = true),
            room("group", isUnread = true),
            room("channel", isUnread = true),
            room("quiet"),
            room("invite", isInvite = true, isUnread = true),
        )
        val zeta = guild("zeta")

        assertEquals(2, directMessagesUnreadCount(rooms, placedRoomIds = setOf(id("channel"), id("quiet"))))
        assertEquals(
            mapOf(zeta.key() to 1),
            guildUnreadCounts(rooms, listOf(zeta), mapOf(zeta.key() to setOf(id("channel"), id("quiet"), id("invite")))),
        )
    }

    @Test
    fun `guilds follow the saved order, then their names`() {
        val alpha = guild("alpha", "Alpha")
        val beta = guild("beta", "Beta")
        val gamma = guild("gamma", null)
        val order = listOf(beta.key(), "gone", gamma.key())

        assertEquals(listOf(beta, gamma, alpha), orderGuilds(listOf(gamma, alpha, beta), order))
        assertEquals(listOf(gamma, alpha, beta), orderGuilds(listOf(beta, alpha, gamma), emptyList())) // unnamed: by room id
    }

    @Test
    fun `categories without visible channels are hidden and channels keep the room-list order`() {
        val empty = id("empty")
        val layout = TacitGuildLayout(
            directChildren = mapOf(lobby to setOf("x"), games to setOf("x"), empty to setOf("x")),
            categories = mapOf(lobby to setOf(id("b"), id("a")), games to setOf(id("c"), id("left")), empty to setOf(id("unknown"))),
            categoryNames = mapOf(lobby to "Lobby"),
        )
        val rooms = listOf(room("a"), room("b"), room("c"), room("left", isLeave = true), room("games", isSpace = true))
        val visible = guildRooms(rooms, setOf(id("a"), id("b"), id("c"), id("left")))

        val groups = guildChannelGroups(rooms, visible, layout, hierarchyNames = emptyMap(), categoryOrder = emptyList())

        assertEquals(
            listOf(TacitChannelGroup(games, "games", listOf("c")), TacitChannelGroup(lobby, "Lobby", listOf("a", "b"))),
            groups,
        )
    }

    @Test
    fun `categories follow the saved order and fall back to hierarchy names`() {
        val layout = TacitGuildLayout(
            directChildren = mapOf(lobby to setOf("x"), games to setOf("x")),
            categories = mapOf(lobby to setOf(id("a")), games to setOf(id("b"))),
            categoryNames = emptyMap(),
        )
        val rooms = listOf(room("a"), room("b"), room("lobby", roomName = lobby.full, isSpace = true))
        val visible = guildRooms(rooms, setOf(id("a"), id("b")))

        val groups = guildChannelGroups(rooms, visible, layout, mapOf(lobby to "Lobby"), categoryOrder = listOf(lobby.full))

        assertEquals(listOf("Lobby", games.full), groups.map { it.categoryName })
    }

    @Test
    fun `a direct child that is a room, not a space, is no category`() {
        val layout = TacitGuildLayout(mapOf(lobby to setOf("x")), mapOf(lobby to setOf(id("a"))), emptyMap())
        val rooms = listOf(room("a"), room("lobby"))

        assertEquals(emptyList(), guildChannelGroups(rooms, guildRooms(rooms, setOf(id("a"))), layout, emptyMap(), emptyList()))
    }

    @Test
    fun `browse lists joinable, joined, invited and knocking rooms by name`() {
        val layout = TacitGuildLayout(
            directChildren = mapOf(id("joined") to setOf("a.org"), id("remote") to setOf("b.org"), id("ghost") to setOf("c.org")),
            categories = mapOf(lobby to setOf(id("invited"), id("left"), id("stored"))),
            categoryNames = emptyMap(),
        )
        val hierarchyNames = mapOf(id("remote") to "Remote", id("stored") to "Stored", id("knocked") to "Knocked")
        val rooms = listOf(
            room("joined", roomName = "Joined"),
            room("invited", roomName = "Invited", isInvite = true),
            room("left", roomName = "Left", isLeave = true),
            room("dm", isDirect = true),
        )
        val candidates = browseCandidates(layout, hierarchyNames)
        val memberships = mapOf(id("stored") to Membership.JOIN, id("knocked") to Membership.KNOCK)

        val entries = browseChannelEntries(rooms, candidates, layout, hierarchyNames, memberships)

        assertEquals(listOf("Invited", "Joined", "Knocked", "Remote", "Stored"), entries.map { it.displayName })
        val byName = entries.associateBy { it.displayName }
        assertEquals(SpaceChannelStatus.NOT_JOINED, byName.getValue("Remote").status)
        assertEquals(true, byName.getValue("Remote").isJoinable)
        assertEquals(setOf("b.org"), byName.getValue("Remote").via)
        assertEquals(SpaceChannelStatus.JOINED, byName.getValue("Stored").status)
        assertEquals(SpaceChannelStatus.KNOCKING, byName.getValue("Knocked").status)
        assertEquals(SpaceChannelStatus.INVITED, byName.getValue("Invited").status)
        assertEquals(false, byName.getValue("Invited").isJoinable)
    }
}
