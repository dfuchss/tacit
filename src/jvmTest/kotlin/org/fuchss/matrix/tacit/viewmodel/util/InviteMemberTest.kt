package org.fuchss.matrix.tacit.viewmodel.util

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import kotlinx.coroutines.test.runTest
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InviteMemberTest {
    private val guild = GuildEntry(
        roomId = RoomId("!guild:example.org"),
        userId = UserId("@tacit:example.org"),
        displayName = "Guild",
        avatarUri = null,
        membership = Membership.JOIN,
    )
    private val calls = mutableListOf<Triple<RoomId, UserId, String?>>()
    private val recordingInvite: suspend (RoomId, UserId, String?) -> Result<Unit> = { roomId, userId, reason ->
        calls += Triple(roomId, userId, reason)
        Result.success(Unit)
    }

    @Test
    fun `the invite goes through the given invite use case with trimmed input`() = runTest {
        val result = guild.inviteMember(recordingInvite, " @alice:example.org ", "  join us ")

        assertTrue(result.isSuccess)
        assertEquals(listOf(Triple<RoomId, UserId, String?>(guild.roomId, UserId("@alice:example.org"), "join us")), calls.toList())
    }

    @Test
    fun `a blank reason is sent as no reason`() = runTest {
        guild.inviteMember(recordingInvite, "@alice:example.org", "   ")

        assertEquals(null, calls.single().third)
    }

    @Test
    fun `a blank user ID is rejected before inviting`() = runTest {
        assertTrue(guild.inviteMember(recordingInvite, "  ", "").isFailure)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `a failing invite is passed on`() = runTest {
        val failure = IllegalStateException("rejected")

        val result = guild.inviteMember({ _, _, _ -> Result.failure<Unit>(failure) }, "@alice:example.org", "")

        assertEquals(failure, result.exceptionOrNull())
    }
}
