package org.fuchss.matrix.tacit

import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import de.connect2x.trixnity.messenger.util.uriFilter
import io.ktor.http.Url
import org.fuchss.matrix.tacit.update.TacitOs
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TacitSingleInstanceTest {
    private val appUri = "org.fuchss.matrix.tacit:"
    private val dir: Path = Files.createTempDirectory("tacit-single-instance")

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private fun lock() = dir.resolve(SINGLE_INSTANCE_LOCK_FILE).writeText("2424")

    @Test
    fun aBareRelaunchWhileAnotherInstanceRunsForwardsTheActivationUri() {
        lock()
        for (os in listOf(TacitOs.WINDOWS, TacitOs.LINUX)) {
            assertContentEquals(arrayOf(activationUri(appUri)), singleInstanceArgs(emptyArray(), appUri, os, dir))
            assertContentEquals(arrayOf(activationUri(appUri)), singleInstanceArgs(arrayOf(" "), appUri, os, dir))
        }
    }

    @Test
    fun aFirstInstanceKeepsItsEmptyArguments() {
        assertContentEquals(emptyArray(), singleInstanceArgs(emptyArray(), appUri, TacitOs.WINDOWS, dir))
    }

    @Test
    fun aLaunchWithAUriKeepsIt() {
        lock()
        val args = arrayOf("$appUri/sso?state=x")
        assertContentEquals(args, singleInstanceArgs(args, appUri, TacitOs.WINDOWS, dir))
    }

    @Test
    fun macOsKeepsItsOwnReopenHandling() {
        lock()
        assertContentEquals(emptyArray(), singleInstanceArgs(emptyArray(), appUri, TacitOs.MACOS, dir))
    }

    @Test
    fun theActivationUriPassesUpstreamsFilterButNoUpstreamRoute() {
        val config = MatrixMultiMessengerConfiguration().apply { this.appUri = this@TacitSingleInstanceTest.appUri }
        val uri = activationUri(appUri)
        assertTrue(uriFilter(config)(uri), "the running instance must not drop it")
        val url = Url(uri)
        // RootRouter / the login workers react to these paths, the room list to `/matrix:roomid/<id>`.
        assertNotEquals("/sso", url.encodedPath)
        assertNotEquals("/oAuth2", url.encodedPath)
        assertTrue(url.rawSegments.size < 3 || url.rawSegments[1] != "matrix:roomid")
    }

    @Test
    fun aLockHeldLikeUpstreamsIsDetected() {
        lock()
        val lockFile = dir.resolve(SINGLE_INSTANCE_LOCK_FILE)
        // Upstream's running instance: a shared lock over the whole file.
        RandomAccessFile(lockFile.toFile(), "rw").use { file ->
            val held = file.channel.tryLock(0, Long.MAX_VALUE, true)
            assertTrue(isSingleInstanceLockHeld(lockFile))
            held.release()
        }
        assertFalse(isSingleInstanceLockHeld(lockFile), "a lock file left behind by a crashed instance")
        assertFalse(isSingleInstanceLockHeld(dir.resolve("missing.lock")))
    }

    @Test
    fun aLockHeldByAnotherProcessIsDetected() {
        lock()
        val lockFile = dir.resolve(SINGLE_INSTANCE_LOCK_FILE)
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val holder = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), LockHolder::class.java.name, lockFile.toString())
            .redirectErrorStream(true)
            .start()
        try {
            assertEquals("locked", holder.inputReader().readLine())
            assertTrue(isSingleInstanceLockHeld(lockFile))
            assertEquals(LAUNCHER_LOGGING_CONFIG, loggingConfigResource(TacitOs.WINDOWS, dir))
            assertEquals(MAIN_LOGGING_CONFIG, loggingConfigResource(TacitOs.MACOS, dir))
        } finally {
            holder.destroyForcibly().waitFor()
        }
        assertFalse(isSingleInstanceLockHeld(lockFile))
        assertEquals(MAIN_LOGGING_CONFIG, loggingConfigResource(TacitOs.WINDOWS, dir))
    }
}

/** Holds a shared lock on the file in `args[0]` like upstream's running instance, until it is killed. */
internal object LockHolder {
    @JvmStatic
    fun main(args: Array<String>) {
        val file = RandomAccessFile(args[0], "rw")
        file.channel.tryLock(0, Long.MAX_VALUE, true) ?: error("could not lock")
        println("locked")
        System.out.flush()
        Thread.sleep(60_000)
    }
}
