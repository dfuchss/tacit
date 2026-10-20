package org.fuchss.matrix.tacit.notification

import de.connect2x.sysnotify.Notification
import de.connect2x.sysnotify.NotificationCapabilities
import de.connect2x.sysnotify.NotificationHandle
import de.connect2x.sysnotify.NotificationHandler
import de.connect2x.sysnotify.NotificationPlatform
import de.connect2x.sysnotify.NotificationPriority
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

    private class FakeHandler(
        override val platform: NotificationPlatform,
        private val failOnClear: Boolean = false,
    ) : NotificationHandler {
        var cleared = 0
        override val name = "fake"
        override val id = "fake"
        override val appId = "fake"
        override val capabilities = NotificationCapabilities(true, true, true, true, true, true, true, true, true, true)
        override val hasPermissions = true
        override val priority = NotificationPriority.DEFAULT
        override val contributesToCounter = true
        override suspend fun push(notification: Notification, tag: String): NotificationHandle = error("unused")
        override suspend fun pop(handle: NotificationHandle) = error("unused")
        override suspend fun clearAll() {
            cleared++
            if (failOnClear) error("boom")
        }
        override suspend fun unregister() = Unit
        override fun close() = Unit
    }

    @Test
    fun cleanupIsOnlyNeededOnApple() {
        NotificationPlatform.entries.forEach {
            assertEquals(it == NotificationPlatform.APPLE, FakeHandler(it).needsLeftoverCleanup(), "$it")
        }
    }

    @Test
    fun onlyAppleHandlersAreCleared() = runTest {
        val handlers = mapOf("a" to FakeHandler(NotificationPlatform.APPLE), "b" to FakeHandler(NotificationPlatform.LINUX))
        clearLeftoverNotifications(handlers.keys) { handlers.getValue(it) }
        assertEquals(1, handlers.getValue("a").cleared)
        assertEquals(0, handlers.getValue("b").cleared)
    }

    @Test
    fun aFailingAccountDoesNotStopTheOthers() = runTest {
        val handlers = mapOf(
            "lookup" to null,
            "fails" to FakeHandler(NotificationPlatform.APPLE, failOnClear = true),
            "ok" to FakeHandler(NotificationPlatform.APPLE),
        )
        clearLeftoverNotifications(handlers.keys) { handlers.getValue(it) ?: error("no handler") }
        assertEquals(1, handlers.getValue("fails")!!.cleared)
        assertEquals(1, handlers.getValue("ok")!!.cleared)
    }
}
