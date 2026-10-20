package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.runBlocking
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
    fun `initial anchor is applied only once`() = runBlocking {
        val anchors = mutableListOf<Int>()
        val state = TimelineScrollState(LazyListState()) { anchors += it }
        state.applyInitialAnchor(5)
        state.applyInitialAnchor(5)
        state.applyInitialAnchor(0)
        assertEquals(listOf(5), anchors)
    }

    @Test
    fun `initial anchor is retried when the scroll did not finish`() = runBlocking {
        val anchors = mutableListOf<Int>()
        var fail = true
        val state = TimelineScrollState(LazyListState()) {
            anchors += it
            if (fail) throw IllegalStateException("cancelled")
        }
        runCatching { state.applyInitialAnchor(3) }
        fail = false
        state.applyInitialAnchor(3)
        state.applyInitialAnchor(3)
        assertEquals(listOf(3, 3), anchors)
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
