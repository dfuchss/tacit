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
}
