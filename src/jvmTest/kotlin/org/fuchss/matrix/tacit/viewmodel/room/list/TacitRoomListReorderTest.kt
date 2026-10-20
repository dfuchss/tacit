package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TacitRoomListReorderTest {
    private fun id(name: String) = RoomId("!$name:example.org")

    private fun guild(name: String, user: String = "tacit", displayName: String? = name) =
        GuildEntry(id(name), UserId("@$user:example.org"), displayName, avatarUri = null, membership = Membership.JOIN)

    @Test
    fun `moving an item takes it out and puts it in at the target index`() {
        val items = listOf("a", "b", "c", "d")

        assertEquals(listOf("b", "c", "a", "d"), items.moved(0, 2))
        assertEquals(listOf("d", "a", "b", "c"), items.moved(3, 0))
        assertEquals(listOf("a", "c", "b", "d"), items.moved(1, 2))
    }

    @Test
    fun `no move for equal or out-of-range indices`() {
        val items = listOf("a", "b")

        assertNull(items.moved(1, 1))
        assertNull(items.moved(-1, 0))
        assertNull(items.moved(0, 2))
        assertNull(emptyList<String>().moved(0, 0))
        assertFalse(isMove(null, 1, 2))
        assertFalse(isMove(0, null, 2))
        assertTrue(isMove(0, 1, 2))
    }

    @Test
    fun `a guild is moved by its key against the current list`() {
        val a = guild("a")
        val b = guild("b")
        val c = guild("c")
        val d = guild("d")

        assertEquals(listOf(b, c, a).map { it.key() }, guildOrderAfterMove(listOf(a, b, c), a.key(), 2))
        // A guild joined above the dragged one mid-drag: the drag of "b" still moves "b", not the guild now at its old index.
        assertEquals(listOf(d, a, c, b).map { it.key() }, guildOrderAfterMove(listOf(d, a, b, c), b.key(), 3))
        // The target index is clamped to a list that shrank mid-drag.
        assertEquals(listOf(b, a).map { it.key() }, guildOrderAfterMove(listOf(a, b), a.key(), 3))
        assertNull(guildOrderAfterMove(listOf(a, c), b.key(), 0)) // the dragged guild is gone
        assertNull(guildOrderAfterMove(listOf(a, b), a.key(), 0))
        assertNull(guildOrderAfterMove(listOf(a.copy(userId = UserId("@other:example.org"))), a.key(), 0))
    }

    @Test
    fun `moving a category reorders the category ids of the groups`() {
        val groups = listOf("lobby", "games", "music").map { TacitChannelGroup(id(it), it, listOf(it)) }

        assertEquals(listOf(id("games"), id("lobby"), id("music")), categoryOrderAfterMove(groups, 0, 1))
        assertEquals(listOf(id("lobby"), id("music"), id("games")), categoryOrderAfterMove(groups, 1, 2))
        assertNull(categoryOrderAfterMove(groups, 0, -1))
        assertNull(categoryOrderAfterMove(groups, 2, 3))
    }

    @Test
    fun `the drop index follows the drag offset in whole pill steps and stays on the rail`() {
        assertEquals(2, guildDropIndex(fromIndex = 2, offsetPx = 20f, stepPx = 62f, size = 5))
        assertEquals(3, guildDropIndex(fromIndex = 2, offsetPx = 40f, stepPx = 62f, size = 5))
        assertEquals(0, guildDropIndex(fromIndex = 2, offsetPx = -100f, stepPx = 62f, size = 5))
        assertEquals(0, guildDropIndex(fromIndex = 1, offsetPx = -1_000f, stepPx = 62f, size = 5))
        assertEquals(4, guildDropIndex(fromIndex = 1, offsetPx = 1_000f, stepPx = 62f, size = 5))
        assertEquals(0, guildDropIndex(fromIndex = 0, offsetPx = 1_000f, stepPx = 62f, size = 0))
    }

    @Test
    fun `the drop hint sits before the target pill, or after it when dragging down`() {
        assertEquals(1, guildDropHintIndex(fromIndex = 3, dropIndex = 1, size = 4))
        assertEquals(3, guildDropHintIndex(fromIndex = 0, dropIndex = 2, size = 4))
        assertEquals(4, guildDropHintIndex(fromIndex = 0, dropIndex = 3, size = 4)) // after the last pill
        assertNull(guildDropHintIndex(fromIndex = 2, dropIndex = 2, size = 4))
        assertNull(guildDropHintIndex(fromIndex = null, dropIndex = 2, size = 4))
        assertNull(guildDropHintIndex(fromIndex = 0, dropIndex = 4, size = 4))
    }

    @Test
    fun `the selected guild is refreshed from the guild list, or dropped once it is gone`() {
        val selected = guild("a", displayName = "Old")
        val renamed = guild("a", displayName = "New")
        val sameRoomOtherAccount = guild("a", user = "other", displayName = "Other")

        assertSame(renamed, reselectGuild(selected, listOf(guild("b"), sameRoomOtherAccount, renamed)))
        assertNull(reselectGuild(selected, listOf(guild("b"), sameRoomOtherAccount)))
        assertNull(reselectGuild(null, listOf(renamed)))
    }
}
