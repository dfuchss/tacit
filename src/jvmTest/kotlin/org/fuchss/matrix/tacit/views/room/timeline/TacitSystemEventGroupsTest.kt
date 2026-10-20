package org.fuchss.matrix.tacit.views.room.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TacitSystemEventGroupsTest {
    private data class Item(val key: String, val info: SystemEventGroupingInfo, val date: String = "today")

    private fun state(key: String, notice: String? = null, rejoin: Boolean = false, unread: Boolean = false, date: String = "today") =
        Item(key, SystemEventGroupingInfo(SystemEventRole.Groupable, standalone = rejoin, breakAfter = unread, undecryptableHistoryInfo = notice), date)

    private fun message(key: String) = Item(key, SystemEventGroupingInfo.Breaking)

    private fun empty(key: String) = Item(key, SystemEventGroupingInfo.Transparent)

    private fun redacted(key: String) = Item(key, SystemEventGroupingInfo.Hidden)

    private fun group(vararg items: Item) = groupSystemEventRuns(items.toList(), { it.info }, { it.date })

    private fun SystemEventRuns<Item>.keys() = groups.map { run -> run.members.map { it.key } }

    private fun SystemEventRuns<Item>.quietKeys() = quiet.map { it.key }.toSet()

    @Test
    fun `member event with the undecryptable history notice joins the run`() {
        val result = group(state("create"), state("ownJoin", notice = "Previous messages cannot be decrypted"), state("power"), state("invite"))
        assertEquals(listOf(listOf("create", "ownJoin", "power", "invite")), result.keys())
        assertEquals("Previous messages cannot be decrypted", result.groups.single().undecryptableHistoryInfo)
        assertEquals(setOf("create", "ownJoin", "power", "invite"), result.quietKeys())
    }

    @Test
    fun `group shows the notice of its oldest member that has one`() {
        val result = group(state("a"), state("b", notice = "first"), state("c", notice = "second"))
        assertEquals("first", result.groups.single().undecryptableHistoryInfo)
    }

    @Test
    fun `group without a notice has none`() {
        assertNull(group(state("a"), state("b")).groups.single().undecryptableHistoryInfo)
    }

    @Test
    fun `isolated member event with the notice keeps upstream rendering`() {
        val result = group(message("m1"), state("ownJoin", notice = "notice"), message("m2"), state("lonely"))
        assertEquals(emptyList(), result.keys())
        // Not quiet: rendered by upstream's holder (accent pill, divider) as before.
        assertEquals(setOf("lonely"), result.quietKeys())
    }

    @Test
    fun `rejoin prompt stays standalone and breaks the run`() {
        val result = group(state("a"), state("b"), state("leave", rejoin = true), state("c"), state("d"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), result.keys())
        assertEquals(setOf("a", "b", "c", "d"), result.quietKeys())
    }

    @Test
    fun `breaking elements, date changes and the unread marker end runs, transparent ones do not`() {
        val result = group(
            state("a"), empty("e1"), state("b"),
            message("m"),
            state("c"), state("d", unread = true), state("f"), state("g"),
            state("h", date = "tomorrow"), state("i", date = "tomorrow"),
        )
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("f", "g"), listOf("h", "i")), result.keys())
    }

    private fun tombstone(key: String) = Item(key, SystemEventGroupingInfo.StandaloneState)

    private fun SystemEventRuns<Item>.followerKeys() = followers.map { it.key }

    @Test
    fun `message after a collapsed group or an isolated system event follows a system event`() {
        val result = group(
            message("m1"), state("invite"), state("join"), message("m2"), message("m3"),
            state("topic"), empty("e"), message("m4"),
        )
        assertEquals(listOf("m2", "m4"), result.followerKeys())
    }

    @Test
    fun `standalone state events are system events too`() {
        val result = group(state("leave", rejoin = true), message("m1"), tombstone("upgrade"), message("m2"))
        assertEquals(listOf("m1", "m2"), result.followerKeys())
        assertEquals(emptyList(), result.keys())
        assertEquals(emptySet(), result.quietKeys())
    }

    @Test
    fun `tombstone breaks a run without joining it`() {
        val result = group(state("a"), state("b"), tombstone("upgrade"), state("c"), state("d"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), result.keys())
    }

    @Test
    fun `first element and messages after messages follow no system event`() {
        assertEquals(emptyList(), group(empty("e"), message("m1"), message("m2")).followerKeys())
    }

    @Test
    fun `message behind a hidden redacted element still follows the system event`() {
        val result = group(state("join"), redacted("r"), message("m"))
        assertEquals(listOf("m"), result.followerKeys())
    }

    @Test
    fun `hidden redacted element still ends a run`() {
        val result = group(state("a"), state("b"), redacted("r"), state("c"), state("d"), redacted("r2"), message("m"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), result.keys())
        assertEquals(listOf("m"), result.followerKeys())
    }

    @Test
    fun `message behind a redacted message follows only that message`() {
        assertEquals(listOf("m1"), group(state("join"), message("m1"), redacted("r"), message("m2")).followerKeys())
    }
}
