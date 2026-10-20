package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class JumpHighlightStateTest {
    /** Outside of a composition nobody sends the snapshot apply notifications `snapshotFlow` waits for. */
    private suspend fun JumpHighlightState.highlightNow(key: String) {
        highlight(key)
        Snapshot.sendApplyNotifications()
        yield()
    }

    @Test
    fun `highlight expires after its duration`() = runBlocking {
        val state = JumpHighlightState(duration = 100.milliseconds)
        val expiry = launch { state.expire() }
        yield()
        state.highlightNow("event")
        assertEquals("event", state.key)
        delay(300)
        assertNull(state.key)
        expiry.cancel()
    }

    @Test
    fun `a new highlight restarts the timer`() = runBlocking {
        val state = JumpHighlightState(duration = 300.milliseconds)
        val expiry = launch { state.expire() }
        yield()
        state.highlightNow("first")
        delay(200)
        state.highlightNow("second")
        delay(200)
        assertEquals("second", state.key)
        delay(300)
        assertNull(state.key)
        expiry.cancel()
    }
}
