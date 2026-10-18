package org.fuchss.matrix.tacit.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TacitUpdaterStateTest {
    private val current = TacitVersion.parse("26.9.2-tacit.2")

    @Test
    fun `staged update must be strictly newer than the running version`() {
        assertTrue(TacitUpdater.isStagedUpdateUsable("v26.9.2-tacit.3", current, force = false))
        assertFalse(TacitUpdater.isStagedUpdateUsable("v26.9.2-tacit.2", current, force = false))
        assertFalse(TacitUpdater.isStagedUpdateUsable("v26.9.2-tacit.1", current, force = false))
        assertFalse(TacitUpdater.isStagedUpdateUsable("garbage", current, force = false))
        assertFalse(TacitUpdater.isStagedUpdateUsable("v26.9.2-tacit.3", null, force = false))
    }

    @Test
    fun `force keeps an equal staged update`() {
        assertTrue(TacitUpdater.isStagedUpdateUsable("v26.9.2-tacit.2", current, force = true))
    }

    @Test
    fun `update environment variables are removed and others kept`() {
        val env = mutableMapOf(
            "TACIT_UPDATE_CURRENT_VERSION" to "1",
            "TACIT_UPDATE_API_URL" to "x",
            "TACIT_UPDATE_FORCE" to "1",
            "PATH" to "/bin",
            "TACIT_OTHER" to "y",
        )
        TacitUpdateHelper.removeUpdateEnvironment(env)
        assertEquals(setOf("PATH", "TACIT_OTHER"), env.keys)
    }

    @Test
    fun `a staged update is not hidden while checking or after a failed check`() {
        val staged = TacitVersion.parse("26.9.2-tacit.3")
        val ready = TacitUpdateState.ReadyToRestart("26.9.2-tacit.3")
        assertEquals(TacitUpdateState.Checking, TacitUpdater.readyOr(null, TacitUpdateState.Checking))
        assertEquals(ready, TacitUpdater.readyOr(staged, TacitUpdateState.Checking))
        assertEquals(ready, TacitUpdater.readyOr(staged, TacitUpdateState.Failed("offline")))
        assertEquals(TacitUpdateState.Failed("offline"), TacitUpdater.readyOr(null, TacitUpdateState.Failed("offline")))
    }

    @Test
    fun `a failed swap is reported separately and a successful one is not`() {
        val failed = TacitUpdater.failureResult("26.9.2-tacit.3", "could not restore the previous version")
        assertEquals("26.9.2-tacit.3: could not restore the previous version", TacitUpdater.failureFromResult(failed))
        assertNull(TacitUpdater.failureFromResult("""{"success":true,"version":"26.9.2-tacit.3","message":"updated"}"""))
        // PowerShell may write a BOM
        assertEquals("v: m", TacitUpdater.failureFromResult("\uFEFF" + """{"success":false,"version":"v","message":"m"}"""))
    }
}
