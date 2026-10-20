package org.fuchss.matrix.tacit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun anAppThatThrowsIsStillShutDown() = runBlocking {
        var shutDown = false
        val error = assertFailsWith<IllegalStateException> {
            runThenShutDown(app = { error("composition failed") }, shutDown = { shutDown = true })
        }
        assertEquals("composition failed", error.message)
        assertTrue(shutDown)
    }

    @Test
    fun aFailingShutdownDoesNotHideTheAppsError() = runBlocking {
        val error = assertFailsWith<IllegalStateException> {
            runThenShutDown(app = { error("composition failed") }, shutDown = { error("close failed") })
        }
        assertEquals("composition failed", error.message)
    }

    @Test
    fun aLaunchDuringTheShutdownIsReported() = runBlocking(Dispatchers.Default) {
        // Like upstream's URI handler: the start-up URI is replayed to every new collector.
        val uris = MutableSharedFlow<String>(replay = 1).apply { tryEmit("tacit:/sso?state=startup") }
        val launchedAgain = runThenShutDown(
            app = { uris.emit("tacit:/tacit-activate") }, // handled by the window, not a late launch
            shutDown = { uris.emit("tacit:/tacit-activate"); delay(100) },
            uris = uris,
        )
        assertTrue(launchedAgain)
    }

    @Test
    fun launchesWhileTheWindowIsOpenAreNotReported() = runBlocking(Dispatchers.Default) {
        val uris = MutableSharedFlow<String>(replay = 1).apply { tryEmit("tacit:/tacit-activate") }
        val launchedAgain = runThenShutDown(
            app = { uris.emit("tacit:/tacit-activate"); delay(100) },
            shutDown = { delay(100) },
            uris = uris,
        )
        assertFalse(launchedAgain, "neither the replayed nor the handled URI counts")
    }

    @Test
    fun aLaunchAfterTheWindowIsGoneIsReported() = runBlocking(Dispatchers.Default) {
        val uris = MutableSharedFlow<String>(replay = 1)
        val launchedAgain = runThenShutDown(
            app = { windowGone -> windowGone(); uris.emit("tacit:/tacit-activate"); delay(100) },
            shutDown = {},
            uris = uris,
        )
        assertTrue(launchedAgain)
    }

    @Test
    fun anAppErrorWinsOverALateLaunch() = runBlocking(Dispatchers.Default) {
        val uris = MutableSharedFlow<String>(replay = 1)
        var launchedAgain: Boolean? = null
        assertFailsWith<IllegalStateException> {
            launchedAgain = runThenShutDown(
                app = { error("composition failed") },
                shutDown = { uris.emit("tacit:/tacit-activate"); delay(100) },
                uris = uris,
            )
        }
        assertEquals(null, launchedAgain, "the error wins; Main exits with 1")
    }
}
