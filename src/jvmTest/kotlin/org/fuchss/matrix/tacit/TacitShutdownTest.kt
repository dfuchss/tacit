package org.fuchss.matrix.tacit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TacitShutdownTest {
    @Test
    fun syncsAreStoppedBeforeTheMessengerIsClosed() = runBlocking {
        val calls = mutableListOf<String>()
        val done = shutDownGracefully(
            stopSyncs = listOf(suspend { calls += "stop a" }, suspend { calls += "stop b" }),
            close = { calls += "close" },
        )
        assertTrue(done)
        assertEquals("close", calls.last())
        assertEquals(setOf("stop a", "stop b"), calls.dropLast(1).toSet())
    }

    @Test
    fun aSyncThatNeverStopsDoesNotBlockTheClose() = runBlocking {
        var closed = false
        val done = shutDownGracefully(
            stopSyncs = listOf(suspend { awaitCancellation() }),
            close = { closed = true },
            syncStopTimeout = 100.milliseconds,
        )
        assertFalse(done)
        assertTrue(closed)
    }

    @Test
    fun aFailingSyncStopDoesNotBlockTheClose() = runBlocking {
        var closed = false
        val done = shutDownGracefully(
            stopSyncs = listOf(suspend { error("client already closed") }),
            close = { closed = true },
        )
        assertTrue(done)
        assertTrue(closed)
    }

    /** The field bug: closing waits on a non-cancellable coroutine that never ends; shutdown must still return. */
    @Test
    fun aCloseThatNeverFinishesIsBounded() = runBlocking {
        val neverCompletes = CompletableDeferred<Unit>()
        val done = shutDownGracefully(
            stopSyncs = emptyList(),
            close = { neverCompletes.await() },
            closeTimeout = 200.milliseconds,
        )
        assertFalse(done)
    }

    @Test
    fun theDefaultTimeoutsOutlastTheSyncRetryBackOff() {
        // trixnity's sync retry back-off is capped at 5 s; the loop only reports STOPPED after it.
        assertTrue(SYNC_STOP_TIMEOUT > 5.seconds)
        assertTrue(CLOSE_TIMEOUT > 0.seconds)
    }
}
