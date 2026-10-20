package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fuchss.matrix.tacit.viewmodel.room.TacitSpaceSettingsRequests
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.testTacitI18n
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TacitRoomListActionsTest {
    private val user = UserId("@tacit:example.org")
    private val invitedGuild = GuildEntry(RoomId("!invited:example.org"), user, "Invited", avatarUri = null, membership = Membership.INVITE)
    private val joinedGuild = invitedGuild.copy(membership = Membership.JOIN)
    private val category = RoomId("!category:example.org")

    private fun TestScope.actions(requests: TacitSpaceSettingsRequests) = TacitRoomListActions(
        scope = backgroundScope,
        i18n = testTacitI18n(),
        onGuildSelected = {},
        onRoomSelected = {},
        guildClient = { null },
        spaceSettingsRequests = requests,
    )

    @Test
    fun `an invited guild files no settings request, a joined one and its categories do`() {
        assertFalse(canRequestSpaceSettings(invitedGuild, invitedGuild.roomId))
        assertTrue(canRequestSpaceSettings(joinedGuild, joinedGuild.roomId))
        assertTrue(canRequestSpaceSettings(joinedGuild, category))
        assertTrue(canRequestSpaceSettings(null, category))
    }

    @Test
    fun `accepting a guild invite drops a settings request for that guild`() = runTest(UnconfinedTestDispatcher()) {
        val requests = TacitSpaceSettingsRequests()
        requests.request(invitedGuild.roomId)

        actions(requests).acceptGuildInvite(invitedGuild)

        assertNull(requests.pendingRoomId.value)
    }

    @Test
    fun `declining a guild invite drops a settings request for that guild only`() = runTest(UnconfinedTestDispatcher()) {
        val requests = TacitSpaceSettingsRequests()
        val actions = actions(requests)

        requests.request(invitedGuild.roomId)
        actions.declineGuildInvite(invitedGuild)
        assertNull(requests.pendingRoomId.value)

        requests.request(category)
        actions.declineGuildInvite(invitedGuild)
        assertEquals(category, requests.pendingRoomId.value)
    }
}
