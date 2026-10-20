package org.fuchss.matrix.tacit

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.util.Collections
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

    /**
     * The field bug: closing waits on a non-cancellable coroutine that never ends; shutdown must still return.
     *
     * The timeout can only end a close that suspends cancellably. That holds for upstream's `closeSuspending()`: it
     * calls `close()` and then waits in `job.join()`, which is cancellable even though the job it waits for never
     * completes. A close that blocked its thread or ran `NonCancellable` would not be bounded by this.
     */
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

    /** The overload `Main` uses, against fakes: every client of the active profile is stopped, then the messenger closed. */
    @Test
    fun theMessengerShutdownStopsTheActiveClientsThenCloses() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        fun client(name: String) = fake<MatrixClient> { method, _ -> if (method == "stopSync") calls += "stop $name" else unexpected(method) }
        val clientMap = mapOf(UserId("@a:example.org") to client("a"), UserId("@b:example.org") to client("b"))
        val clients = fake<MatrixClients> { method, _ -> if (method == "getValue") clientMap else unexpected(method) }
        val messengerDi = koinApplication { modules(module { single<MatrixClients> { clients } }) }.koin
        val messenger = fake<MatrixMessenger> { method, _ -> if (method == "getDi") messengerDi else unexpected(method) }
        val multiMessenger = fakeMultiMessenger(messenger) { calls += "close" }

        assertTrue(shutDownGracefully(multiMessenger))
        assertEquals("close", calls.last())
        assertEquals(setOf("stop a", "stop b"), calls.dropLast(1).toSet())
    }

    @Test
    fun theMessengerShutdownClosesWithoutAnActiveProfile() = runBlocking {
        var closed = false
        assertTrue(shutDownGracefully(fakeMultiMessenger(null) { closed = true }))
        assertTrue(closed)
    }

    private fun fakeMultiMessenger(active: MatrixMessenger?, close: () -> Unit) = fake<MatrixMultiMessenger> { method, _ ->
        when (method) {
            "getActiveMatrixMessenger" -> MutableStateFlow(active)
            "closeSuspending" -> close()
            else -> unexpected(method)
        }
    }

    private fun unexpected(method: String): Nothing = error("unexpected call $method")

    /**
     * A [Proxy] implementing [T]; [handler] gets the method name and arguments. A suspend function returns its result
     * directly (it never suspends), so returning `Unit` from `stopSync` or `closeSuspending` completes them.
     */
    private inline fun <reified T : Any> fake(crossinline handler: (method: String, args: Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "fake ${T::class.simpleName}"
                else -> handler(method.name, args ?: emptyArray()) ?: Unit
            }
        } as T
}
