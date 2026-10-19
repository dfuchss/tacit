package org.fuchss.matrix.tacit.viewmodel.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

class ExclusiveLaunchTest {
    @Test
    fun secondLaunchWhileRunningIsRejected() = runBlocking<Unit> {
        val inProgress = MutableStateFlow(false)
        val release = CompletableDeferred<Unit>()
        var runs = 0

        val first = launchExclusive(inProgress) { runs++; release.await() }
        // The flag is claimed synchronously, before the coroutine has even started.
        assertTrue(inProgress.value)
        val second = launchExclusive(inProgress) { runs++ }

        assertNotNull(first)
        assertNull(second)
        release.complete(Unit)
        first.join()
        assertEquals(1, runs)
        assertFalse(inProgress.value)
    }

    @Test
    fun flagIsReleasedWhenTheActionThrows() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
        val inProgress = MutableStateFlow(false)

        scope.launchExclusive(inProgress) { error("boom") }!!.join()

        assertFalse(inProgress.value)
        assertNotNull(scope.launchExclusive(inProgress) {})
    }

    @Test
    fun flagIsReleasedWhenTheScopeIsAlreadyCancelled() = runBlocking<Unit> {
        val scope = CoroutineScope(Job().apply { cancel() })
        val inProgress = MutableStateFlow(false)

        // The block never runs, so a try/finally inside it could never reset the flag.
        scope.launchExclusive(inProgress) { fail("must not run") }!!.join()

        assertFalse(inProgress.value)
    }

    @Test
    fun customIdleAndBusyValues() = runBlocking<Unit> {
        val joining = MutableStateFlow<String?>(null)
        val release = CompletableDeferred<Unit>()

        val job = launchExclusive(joining, idle = null, busy = "!a:example.org") { release.await() }
        assertEquals("!a:example.org", joining.value)
        assertNull(launchExclusive(joining, idle = null, busy = "!b:example.org") {})

        release.complete(Unit)
        job!!.join()
        assertNull(joining.value)
    }
}
