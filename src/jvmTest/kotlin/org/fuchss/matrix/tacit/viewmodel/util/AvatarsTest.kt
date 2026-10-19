package org.fuchss.matrix.tacit.viewmodel.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarsTest {
    @Test
    fun `mapConcurrently preserves order`() = runBlocking {
        assertEquals(listOf(2, 4, 6), listOf(1, 2, 3).mapConcurrently { it * 2 })
    }

    @Test
    fun `mapConcurrently does not serialise elements`() = runBlocking {
        val second = CompletableDeferred<Unit>()
        // The first element only finishes once the second has started; sequential execution would deadlock.
        val result = withTimeout(5_000) {
            listOf(1, 2).mapConcurrently { value ->
                if (value == 1) second.await() else second.complete(Unit)
                value
            }
        }
        assertEquals(listOf(1, 2), result)
    }

    @Test
    fun `mapConcurrently never exceeds the parallelism bound`() = runBlocking {
        val running = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)
        val result = (1..20).toList().mapConcurrently(parallelism = 3) { value ->
            val now = running.incrementAndGet()
            maxRunning.updateAndGet { maxOf(it, now) }
            delay(10)
            running.decrementAndGet()
            value
        }
        assertEquals((1..20).toList(), result)
        assertEquals(3, maxRunning.get())
    }
}
