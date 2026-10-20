package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TacitRoomListUiStateTest {
    private val rooms = listOf(
        TacitDmRoom("quiet", isUnread = false, isOnline = false),
        TacitDmRoom("unread", isUnread = true, isOnline = false),
        TacitDmRoom("online", isUnread = false, isOnline = true),
        TacitDmRoom("both", isUnread = true, isOnline = true),
    )

    @Test
    fun `the DM filters keep unread or online rooms in order`() {
        assertEquals(listOf("quiet", "unread", "online", "both"), rooms.filteredBy(DmFilter.ALL))
        assertEquals(listOf("unread", "both"), rooms.filteredBy(DmFilter.UNREAD))
        assertEquals(listOf("online", "both"), rooms.filteredBy(DmFilter.ONLINE))
        assertEquals(TacitDmOverview(chats = 4, online = 2, unread = 2), TacitDmOverview.of(4, rooms))
    }

    @Test
    fun `picking the active DM filter again goes back to all`() {
        assertEquals(DmFilter.UNREAD, DmFilter.ALL.toggled(DmFilter.UNREAD))
        assertEquals(DmFilter.ALL, DmFilter.UNREAD.toggled(DmFilter.UNREAD))
        assertEquals(DmFilter.ONLINE, DmFilter.UNREAD.toggled(DmFilter.ONLINE))
        assertEquals(DmFilter.ALL, DmFilter.ALL.toggled(DmFilter.ALL))
    }

    @Test
    fun `scroll keys separate the DM home per account and guilds per account`() {
        val alice = UserId("@alice:example.org")
        val bob = UserId("@bob:example.org")
        val guild = GuildEntry(RoomId("!g:example.org"), alice, "G", avatarUri = null, membership = Membership.JOIN)

        assertEquals("dm:@alice:example.org", roomListScrollKey(RoomListMode.DirectMessages, alice))
        assertEquals("dm:@bob:example.org", roomListScrollKey(RoomListMode.DirectMessages, bob))
        assertEquals("dm:*", roomListScrollKey(RoomListMode.DirectMessages, null))
        assertEquals("guild:@alice:example.org:!g:example.org", roomListScrollKey(RoomListMode.GuildChannels(guild), bob))
    }

    @Test
    fun `scroll memory keeps only the most recently saved keys`() {
        val memory = TacitScrollMemory(capacity = 2)
        memory["a"] = TacitScrollPosition(1, 10)
        memory["b"] = TacitScrollPosition(2, 20)
        memory["a"] = TacitScrollPosition(3, 30) // refreshes a
        memory["c"] = TacitScrollPosition(4, 40) // evicts b

        assertEquals(2, memory.size)
        assertEquals(TacitScrollPosition(3, 30), memory["a"])
        assertNull(memory["b"])
        assertEquals(TacitScrollPosition(4, 40), memory["c"])
    }

    @Test
    fun `a selected invite guild without its own invite row falls back to the guild invite row`() {
        val invite = GuildEntry(RoomId("!g:example.org"), UserId("@a:example.org"), "G", null, Membership.INVITE)
        val state = TacitRoomListUiState(rail = TacitGuildRailState(selectedGuild = invite))

        assertEquals(invite, state.selectedGuildInviteFallback)
        assertEquals(RoomListMode.GuildChannels(invite), state.mode)
        assertNull(TacitRoomListUiState().selectedGuildInviteFallback)
    }
}
