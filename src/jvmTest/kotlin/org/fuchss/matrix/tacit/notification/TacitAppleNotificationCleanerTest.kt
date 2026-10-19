package org.fuchss.matrix.tacit.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class TacitAppleNotificationCleanerTest {
    private val quiet = 1.seconds


    private fun script(vararg steps: Pair<Long, Int>): Flow<Int> = flow {
        var now = 0L
        for ((at, value) in steps) {
            delay(at - now)
            now = at
            emit(value)
        }
        delay(5_000)
    }

    private suspend fun fired(source: Flow<Int>): Int {
        var n = 0
        source.allReadSignals(quiet).collect { n++ }
        return n
    }

    @Test
    fun initialZeroFires() = runTest { assertEquals(1, fired(script(0L to 0))) }

    @Test
    fun initialNonZeroDoesNotFire() = runTest { assertEquals(0, fired(script(0L to 3))) }

    @Test
    fun transitionToZeroFires() = runTest { assertEquals(1, fired(script(0L to 2, 100L to 0))) }

    @Test
    fun fastUnreadAfterNewMessageStillFires() = runTest { assertEquals(1, fired(script(0L to 0, 10L to 1, 200L to 0))) }

    @Test
    fun burstOfCountsFiresOnce() = runTest { assertEquals(1, fired(script(0L to 3, 10L to 2, 20L to 1, 30L to 0))) }

    @Test
    fun staysZeroFiresOnlyOnce() = runTest { assertEquals(1, fired(script(0L to 0, 3_000L to 0))) }

    @Test
    fun newMessageAfterQuietPeriodFiresAgainOnNextZero() = runTest {
        assertEquals(2, fired(script(0L to 0, 3_000L to 1, 6_000L to 0)))
    }

    @Test
    fun nonZeroBeforeDebounceElapsesSuppresses() = runTest { assertEquals(0, fired(script(0L to 2, 100L to 0, 500L to 1))) }
}
