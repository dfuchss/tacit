package org.fuchss.matrix.tacit.viewmodel.room

import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TacitSpaceSettingsRequestsTest {
    private val guild = RoomId("!guild:example.org")
    private val category = RoomId("!category:example.org")

    @Test
    fun `a request is consumed exactly once by its own room`() {
        val requests = TacitSpaceSettingsRequests()
        requests.request(guild)

        assertFalse(requests.consume(category))
        assertTrue(requests.consume(guild))
        assertFalse(requests.consume(guild))
        assertNull(requests.pendingRoomId.value)
    }

    @Test
    fun `the latest request wins`() {
        val requests = TacitSpaceSettingsRequests()
        requests.request(guild)
        requests.request(category)

        assertFalse(requests.consume(guild))
        assertTrue(requests.consume(category))
    }

    @Test
    fun `opening another room discards the pending request`() {
        val requests = TacitSpaceSettingsRequests()
        requests.request(guild)

        requests.discardUnlessFor(category)

        assertNull(requests.pendingRoomId.value)
        assertFalse(requests.consume(guild))
    }

    @Test
    fun `opening the requested room keeps the request for it`() {
        val requests = TacitSpaceSettingsRequests()
        requests.request(guild)

        requests.discardUnlessFor(guild)

        assertEquals(guild, requests.pendingRoomId.value)
        assertTrue(requests.consume(guild))
    }

    @Test
    fun `discarding drops only a request for that room`() {
        val requests = TacitSpaceSettingsRequests()
        requests.request(guild)

        requests.discard(category)
        assertEquals(guild, requests.pendingRoomId.value)
        requests.discard(guild)
        assertNull(requests.pendingRoomId.value)
    }

    // ---- the room view model's hand-over ----

    private class OpenedSettings {
        var count = 0
    }

    private fun TestScope.handOver(roomId: RoomId, requests: TacitSpaceSettingsRequests, opened: OpenedSettings) =
        TacitSpaceSettingsHandOver(roomId, requests, backgroundScope) { opened.count++ }

    @Test
    fun `creating the room view model discards a request for another room`() = runTest(UnconfinedTestDispatcher()) {
        val requests = TacitSpaceSettingsRequests()
        val opened = OpenedSettings()
        requests.request(guild)

        val handOver = handOver(category, requests, opened)

        assertNull(requests.pendingRoomId.value)
        assertEquals(TacitRoomSettingsMode.ROOM, handOver.settingsMode.value)
        assertEquals(0, opened.count)
    }

    @Test
    fun `the room view model of the requested space opens its settings in SPACE mode`() = runTest(UnconfinedTestDispatcher()) {
        val requests = TacitSpaceSettingsRequests()
        val opened = OpenedSettings()
        requests.request(guild)

        val handOver = handOver(guild, requests, opened)

        assertEquals(TacitRoomSettingsMode.SPACE, handOver.settingsMode.value)
        assertEquals(1, opened.count)
        assertNull(requests.pendingRoomId.value)
    }

    @Test
    fun `a request for the already open space reuses its view model`() = runTest(UnconfinedTestDispatcher()) {
        val requests = TacitSpaceSettingsRequests()
        val opened = OpenedSettings()
        val handOver = handOver(guild, requests, opened)
        assertEquals(TacitRoomSettingsMode.ROOM, handOver.settingsMode.value)

        requests.request(guild)

        assertEquals(TacitRoomSettingsMode.SPACE, handOver.settingsMode.value)
        assertEquals(1, opened.count)
        assertNull(requests.pendingRoomId.value)

        requests.request(category) // a request for another space is left for that space's view model
        assertEquals(1, opened.count)
        assertEquals(category, requests.pendingRoomId.value)
    }
}
