package org.fuchss.matrix.tacit.views.room.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineScrollStateTest {
    @Test
    fun `pinned to the end only with the newest item flush at the bottom`() {
        assertTrue(isPinnedToEnd(0, 0))
        assertFalse(isPinnedToEnd(0, 12))
        assertFalse(isPinnedToEnd(1, 0))
        assertFalse(isPinnedToEnd(null, null))
    }

    @Test
    fun `follows a new newest element only when pinned before`() {
        assertTrue(shouldFollowNewest("a", "b", wasPinnedToEnd = true))
        assertFalse(shouldFollowNewest("a", "b", wasPinnedToEnd = false))
    }

    @Test
    fun `does not follow on the first look or without a change`() {
        assertFalse(shouldFollowNewest(null, "b", wasPinnedToEnd = true))
        assertFalse(shouldFollowNewest("a", "a", wasPinnedToEnd = true))
    }

    @Test
    fun `scroll request is acknowledged and cleared`() {
        val requests = TimelineScrollRequests()
        requests.request("event")
        assertEquals("event", requests.pending)
        requests.complete("event")
        assertNull(requests.pending)
        assertEquals("event", requests.finished.value)
    }
}
