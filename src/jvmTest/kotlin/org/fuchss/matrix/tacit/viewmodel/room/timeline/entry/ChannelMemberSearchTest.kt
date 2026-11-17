package org.fuchss.matrix.tacit.viewmodel.room.timeline.entry

import de.connect2x.trixnity.core.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelMemberSearchTest {
    private val alice = ChannelMemberEntry(UserId("@alice:example.org"), "Alice Liddell", isSelf = true, avatarImage = null)
    private val bob = ChannelMemberEntry(UserId("@bob:matrix.test"), "Bobby", isSelf = false, avatarImage = null)
    private val members = listOf(alice, bob)

    @Test
    fun blankQueryKeepsEveryone() {
        assertEquals(members, members.matchingSearch(""))
        assertEquals(members, members.matchingSearch("   "))
    }

    @Test
    fun matchesDisplayNameIgnoringCaseAndWhitespace() {
        assertEquals(listOf(alice), members.matchingSearch("  liddELL "))
    }

    @Test
    fun matchesMatrixId() {
        assertEquals(listOf(bob), members.matchingSearch("matrix.test"))
    }

    @Test
    fun noMatchGivesEmptyList() {
        assertEquals(emptyList(), members.matchingSearch("carol"))
    }
}
