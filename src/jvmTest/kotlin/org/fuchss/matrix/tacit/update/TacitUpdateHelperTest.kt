package org.fuchss.matrix.tacit.update

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.test.AfterTest
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the generated `sh` helper for real against a dummy install. The Linux flavor is used because its
 * relaunch (`bin/Tacit`) can be observed without starting anything; the swap logic is shared with macOS.
 */
class TacitUpdateHelperTest {
    private val tempDirs = mutableListOf<java.nio.file.Path>()

    @AfterTest
    fun cleanUpTempDirs() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
        tempDirs.clear()
    }

    private val isUnix = !System.getProperty("os.name").startsWith("Windows")
    private val isMac = System.getProperty("os.name").startsWith("Mac")

    private class Fixture(val base: Path) {
        val install: Path = base.resolve("My Apps/Tacit's Install")
        val staging: Path = base.resolve("app data/updates/v26.9.2-tacit.9")
        val staged: Path = staging.resolve("extracted/Tacit")
        val result: Path = base.resolve("app data/updates/update-result.json")
        val relaunched: Path = base.resolve("relaunched")

        init {
            for ((root, marker) in listOf(install to "old", staged to "new")) {
                root.resolve("bin").createDirectories()
                root.resolve("marker").writeText(marker)
                root.resolve("bin/Tacit").writeText("#!/bin/sh\necho \"$marker\" > '$relaunched'\n")
                root.resolve("bin/Tacit").toFile().setExecutable(true)
            }
        }

        fun params(pid: Long) = TacitUpdateHelperParams(
            os = TacitOs.LINUX,
            pid = pid,
            installRoot = install,
            stagedRoot = staged,
            stagingDir = staging,
            resultFile = result,
            version = "26.9.2-tacit.9",
        )
    }

    private fun runHelper(fixture: Fixture, appSeconds: Int): Pair<Process, Process> {
        val app = ProcessBuilder("sleep", appSeconds.toString()).start()
        val script = fixture.staging.resolve("helper.sh")
        script.writeText(TacitUpdateHelper.shScript(fixture.params(app.pid())))
        val helper = ProcessBuilder("/bin/sh", script.toString())
            .directory(fixture.staging.toFile())
            .redirectErrorStream(true)
            .redirectOutput(fixture.staging.resolve("helper.log").toFile())
            .start()
        return app to helper
    }

    private fun awaitFile(path: Path) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (path.exists() && path.readText().isNotBlank()) return
            Thread.sleep(50)
        }
        fail("timed out waiting for $path")
    }

    @Test
    fun waitsForTheAppThenSwapsWithoutKeepingABackup() {
        assumeTrue("needs a Unix sh", isUnix)
        val fixture = Fixture(Files.createTempDirectory("tacit helper").also(tempDirs::add))
        val (app, helper) = runHelper(fixture, appSeconds = 2)

        // Genuinely timing-based: give the helper time to (wrongly) swap while the app is still alive.
        Thread.sleep(800)
        assertTrue(app.isAlive)
        assertTrue(helper.isAlive, "the helper must still be waiting")
        assertEquals("old", fixture.install.resolve("marker").readText(), "the install must stay untouched while the app runs")

        assertTrue(helper.waitFor(20, TimeUnit.SECONDS))
        assertEquals(0, helper.exitValue(), fixture.staging.resolve("helper.log").readText())
        assertEquals("new", fixture.install.resolve("marker").readText())
        assertFalse(fixture.staged.exists())
        assertFalse(fixture.install.resolveSibling("${fixture.install.fileName}.old-update").exists())
        assertContains(fixture.result.readText(), "\"success\":true")
        awaitFile(fixture.relaunched)
        assertEquals("new", fixture.relaunched.readText().trim())
    }

    @Test
    fun restoresTheBackupWhenTheMoveFails() {
        // An immutable staged folder makes the second rename fail; chflags is macOS only.
        assumeTrue("chflags is macOS only", isMac)
        val fixture = Fixture(Files.createTempDirectory("tacit helper").also(tempDirs::add))
        ProcessBuilder("chflags", "uchg", fixture.staged.toString()).start().waitFor()
        try {
            val (_, helper) = runHelper(fixture, appSeconds = 1)
            assertTrue(helper.waitFor(20, TimeUnit.SECONDS))
            val log = fixture.staging.resolve("helper.log").readText()
            assertContains(log, "restoring the previous version")
            assertEquals("old", fixture.install.resolve("marker").readText())
            assertFalse(fixture.install.resolveSibling("${fixture.install.fileName}.old-update").exists())
            assertContains(fixture.result.readText(), "\"success\":false")
            awaitFile(fixture.relaunched)
            assertEquals("old", fixture.relaunched.readText().trim())
        } finally {
            ProcessBuilder("chflags", "-R", "nouchg", fixture.staged.toString()).start().waitFor()
        }
    }

    @Test
    fun quotesPathsForBothShells() {
        val fixture = TacitUpdateHelperParams(
            os = TacitOs.WINDOWS,
            pid = 42,
            installRoot = Path.of("/Users/O'Brien/Tacit ’x’"),
            stagedRoot = Path.of("/tmp/staged"),
            stagingDir = Path.of("/tmp"),
            resultFile = Path.of("/tmp/result.json"),
            version = "26.9.2-tacit.9",
        )
        assertContains(TacitUpdateHelper.powerShellScript(fixture), "\$Install = '/Users/O''Brien/Tacit ’’x’’'")
        assertContains(TacitUpdateHelper.shScript(fixture), "INSTALL='/Users/O'\\''Brien/Tacit ’x’'")
    }

    @Test
    fun relaunchesOnlyAfterTheAppHasExited() {
        assumeTrue("needs a Unix sh", isUnix)
        val base = Files.createTempDirectory("tacit relaunch").also(tempDirs::add)
        val marker = base.resolve("it's relaunched")
        val app = ProcessBuilder("sleep", "2").start()
        val launch = listOf("/bin/sh", "-c", "echo \"\$1\" > \"\$2\"", "sh", "relaunched", marker.toString())
        val waiter = TacitUpdateHelper.relaunchAfterExit(TacitOs.LINUX, app.pid(), launch)

        // Genuinely timing-based: give the waiter time to (wrongly) launch while the app is still alive.
        Thread.sleep(800)
        assertTrue(app.isAlive)
        assertFalse(marker.exists(), "nothing is started while the app runs")
        assertTrue(waiter.waitFor(20, TimeUnit.SECONDS))
        awaitFile(marker)
        assertEquals("relaunched", marker.readText().trim())
    }

    @Test
    fun theWindowsRelaunchWaitsForThePidAndQuotesTheLauncher() {
        val command = TacitUpdateHelper.relaunchAfterExitCommand(TacitOs.WINDOWS, 42, listOf("C:\\Users\\O'Brien\\Tacit\\Tacit.exe"))
        assertEquals("-EncodedCommand", command[command.size - 2])
        val script = String(java.util.Base64.getDecoder().decode(command.last()), Charsets.UTF_16LE)
        assertContains(script, "Wait-Process -Id 42 -Timeout ${TacitUpdateHelper.RELAUNCH_WAIT_SECONDS}")
        assertContains(script, "if (-not (Get-Process -Id 42 -ErrorAction SilentlyContinue))")
        assertContains(script, "Start-Process -FilePath 'C:\\Users\\O''Brien\\Tacit\\Tacit.exe' }")
    }
}
