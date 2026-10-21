package org.fuchss.matrix.tacit.update

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TacitUpdateStagingTest {
    private val tempDirs = mutableListOf<java.nio.file.Path>()

    @AfterTest
    fun cleanUpTempDirs() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
        tempDirs.clear()
    }

    private fun zip(entries: Map<String, String>): Path {
        val file = Files.createTempFile("tacit-update", ".zip").also(tempDirs::add)
        ZipOutputStream(Files.newOutputStream(file)).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    @Test
    fun safeResolveRejectsEntriesOutsideTheTarget() {
        val target = Files.createTempDirectory("tacit-staging").also(tempDirs::add)
        assertEquals(target.resolve("Tacit/bin/Tacit"), TacitUpdateStaging.safeResolve(target, "Tacit/bin/Tacit"))
        assertFailsWith<IOException> { TacitUpdateStaging.safeResolve(target, "../evil") }
        assertFailsWith<IOException> { TacitUpdateStaging.safeResolve(target, "Tacit/../../evil") }
        assertFailsWith<IOException> { TacitUpdateStaging.safeResolve(target, "..\\evil") }
        assertFailsWith<IOException> { TacitUpdateStaging.safeResolve(target, "/etc/passwd") }
        assertFailsWith<IOException> { TacitUpdateStaging.safeResolve(target, "") }
    }

    @Test
    fun extractZipRefusesZipSlip() {
        val root = Files.createTempDirectory("tacit-staging").also(tempDirs::add)
        val target = root.resolve("target")
        val archive = zip(mapOf("Tacit/ok.txt" to "ok", "../escaped.txt" to "evil"))
        assertFailsWith<IOException> { TacitUpdateStaging.extractZip(archive, target) }
        assertFalse(root.resolve("escaped.txt").exists())
    }

    @Test
    fun stagesALinuxZipAndRestoresExecutableBits() {
        val staging = Files.createTempDirectory("tacit-staging").also(tempDirs::add)
        val archive = zip(
            mapOf(
                "Tacit/bin/Tacit" to "#!/bin/sh\n",
                "Tacit/lib/runtime/bin/java" to "",
                "Tacit/lib/runtime/lib/jspawnhelper" to "",
                "Tacit/lib/app/Tacit.cfg" to "cfg",
            )
        )
        val app = TacitUpdateStaging.stageZip(TacitOs.LINUX, archive, staging)
        assertEquals(staging.resolve("extracted/Tacit"), app)
        assertEquals("cfg", app.resolve("lib/app/Tacit.cfg").readText())
        if (!System.getProperty("os.name").startsWith("Windows")) {
            assertTrue(app.resolve("bin/Tacit").isExecutable())
            assertTrue(app.resolve("lib/runtime/bin/java").isExecutable())
            assertTrue(app.resolve("lib/runtime/lib/jspawnhelper").isExecutable())
        }
    }

    @Test
    fun rejectsAZipWithoutTheLauncher() {
        val staging = Files.createTempDirectory("tacit-staging").also(tempDirs::add)
        val archive = zip(mapOf("Tacit/app/Tacit.cfg" to "cfg"))
        assertFailsWith<IOException> { TacitUpdateStaging.stageZip(TacitOs.WINDOWS, archive, staging) }
    }

    @Test
    fun placesTheAppNextToTheInstallReplacingALeftover() {
        val base = Files.createTempDirectory("tacit-staging").also(tempDirs::add)
        val install = base.resolve("apps/Tacit").also { it.resolve("bin").toFile().mkdirs() }
        val leftover = TacitUpdateStaging.newUpdatePath(install).also { it.toFile().mkdirs() }
        leftover.resolve("old.txt").toFile().writeText("old")
        val staged = base.resolve("data/extracted/Tacit").also { it.resolve("bin").toFile().mkdirs() }
        staged.resolve("bin/Tacit").toFile().writeText("new")

        val placed = TacitUpdateStaging.placeNextToInstall(TacitOs.LINUX, staged, install)
        assertEquals(base.resolve("apps/Tacit.new-update"), placed)
        assertEquals("new", placed.resolve("bin/Tacit").readText())
        assertFalse(placed.resolve("old.txt").exists())
        assertFalse(staged.exists())
        assertTrue(install.resolve("bin").exists(), "the install is untouched")
    }
}
