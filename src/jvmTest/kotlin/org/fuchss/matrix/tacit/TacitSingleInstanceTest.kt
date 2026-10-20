package org.fuchss.matrix.tacit

import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import de.connect2x.trixnity.messenger.util.uriFilter
import io.ktor.http.Url
import org.fuchss.matrix.tacit.update.TacitOs
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
}
