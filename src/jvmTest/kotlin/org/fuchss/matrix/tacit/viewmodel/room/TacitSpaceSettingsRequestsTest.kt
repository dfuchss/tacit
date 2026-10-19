package org.fuchss.matrix.tacit.viewmodel.room

import de.connect2x.trixnity.core.model.RoomId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
