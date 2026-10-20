package org.fuchss.matrix.tacit.viewmodel.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TacitActionTest {
    private fun TestScope.action() = TacitAction(backgroundScope, testTacitI18n())

    @Test
    fun `a second start while running is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val action = action()
        val release = CompletableDeferred<Unit>()
        var runs = 0
        var successes = 0

        val first = action.launch(TacitI18n::tacitCreateGuildFailed, onSuccess = { _: Unit -> successes++ }) {
            runs++
            release.await()
            Result.success(Unit)
        }
        val second = action.launch(TacitI18n::tacitCreateGuildFailed, onSuccess = { _: Unit -> successes++ }) {
            runs++
            Result.success(Unit)
        }

        assertTrue(first != null)
        assertNull(second)
        assertTrue(action.isCreating.value)
        release.complete(Unit)
        assertFalse(action.isCreating.value)
        assertEquals(1, runs)
        assertEquals(1, successes)
    }

    @Test
    fun `a failure is localised and cleared by the next start or a dismiss`() = runTest(UnconfinedTestDispatcher()) {
        val action = action()

        action.launch(TacitI18n::tacitCreateGuildFailed) { Result.failure<Unit>(IllegalStateException("boom")) }
        assertEquals("Could not create guild: boom", action.error.value)
        assertFalse(action.isCreating.value)

        val release = CompletableDeferred<Unit>()
        action.launch(TacitI18n::tacitCreateGuildFailed) { release.await(); Result.success(Unit) }
        assertNull(action.error.value)
        release.complete(Unit)

        action.launch(TacitI18n::tacitCreateGuildFailed) { Result.failure<Unit>(IllegalStateException("again")) }
        action.errorDismiss()
        assertNull(action.error.value)
    }

    @Test
    fun `a repeated identical failure is a new failure`() = runTest(UnconfinedTestDispatcher()) {
        val action = action()
        val fail = { action.launch(TacitI18n::tacitCreateGuildFailed) { Result.failure<Unit>(IllegalStateException("boom")) } }

        fail()
        val first = action.failure.value
        fail()
        val second = action.failure.value

        assertEquals("Could not create guild: boom", first?.message)
        assertEquals(first?.message, second?.message)
        assertNotEquals(first, second)
        assertEquals("Could not create guild: boom", action.error.value)

        action.errorDismiss()
        assertNull(action.failure.value)
    }

    @Test
    fun `a cancelled action is no longer running`() = runTest(UnconfinedTestDispatcher()) {
        val action = action()
        val job = action.launch(TacitI18n::tacitCreateGuildFailed) { CompletableDeferred<Result<Unit>>().await() }

        job?.cancel()

        assertFalse(action.isCreating.value)
        assertNull(action.error.value)
    }
}
