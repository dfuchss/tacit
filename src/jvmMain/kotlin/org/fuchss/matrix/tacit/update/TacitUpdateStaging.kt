package org.fuchss.matrix.tacit.update

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.*

private val stagingLog = Logger("org.fuchss.matrix.tacit.update.TacitUpdateStaging")

/**
 * Prepares a downloaded release under `<app data>/updates/<tag>/`. Nothing here ever writes next to or inside
 * the install; the install is only read (to compare the bundle identifier).
 */
object TacitUpdateStaging {
    /** Resolves [entryName] below [target] and refuses entries that would escape it (zip slip). */
    fun safeResolve(target: Path, entryName: String): Path {
        val root = target.toAbsolutePath().normalize()
        val resolved = root.resolve(entryName.replace('\\', '/')).normalize()
        if (resolved == root || !resolved.startsWith(root)) throw IOException("Refusing zip entry outside of the target: $entryName")
        return resolved
    }

    fun extractZip(zip: Path, target: Path) {
        Files.createDirectories(target)
        ZipFile(zip.toFile()).use { file ->
            for (entry in file.entries()) {
                val out = safeResolve(target, entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(out)
                } else {
                    Files.createDirectories(out.parent)
                    file.getInputStream(entry).use { Files.copy(it, out, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
    }

    /** Extracts the platform zip and returns the staged `Tacit` folder. */
    fun stageZip(os: TacitOs, zip: Path, stagingDir: Path): Path {
        val extracted = stagingDir.resolve("extracted")
        if (extracted.exists()) deleteRecursively(extracted)
        extractZip(zip, extracted)
        val app = extracted.resolve("Tacit")
        val launcher = when (os) {
            TacitOs.WINDOWS -> app.resolve("Tacit.exe")
            else -> app.resolve("bin").resolve("Tacit")
        }
        if (!launcher.isRegularFile()) throw IOException("The update archive does not contain ${extracted.relativize(launcher)}")
        if (os == TacitOs.LINUX) restoreExecutableBits(app)
        return app
    }

    /** `ZipFile` drops the unix modes, so the launcher and the bundled runtime binaries get them back. */
    private fun restoreExecutableBits(app: Path) {
        val executables = buildList {
            listOf(app.resolve("bin"), app.resolve("lib/runtime/bin")).filter { it.isDirectory() }
                .forEach { dir -> dir.listDirectoryEntries().filter { it.isRegularFile() }.forEach(::add) }
            Files.walk(app).use { paths -> paths.filter { it.fileName?.toString() == "jspawnhelper" }.forEach(::add) }
        }
        for (file in executables) {
            runCatching {
                file.setPosixFilePermissions(
                    file.getPosixFilePermissions() + setOf(
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_EXECUTE,
                    )
                )
            }.onFailure { stagingLog.warn(it) { "could not mark $file executable" } }
        }
    }

    /** Copies `Tacit.app` out of the DMG and returns the staged bundle. */
    fun stageDmg(dmg: Path, stagingDir: Path, installedApp: Path?): Path {
        val mountPoint = stagingDir.resolve("mnt")
        val staged = stagingDir.resolve("Tacit.app")
        if (staged.exists()) deleteRecursively(staged)
        Files.createDirectories(mountPoint)
        run(
            "hdiutil", "attach", "-nobrowse", "-readonly", "-noautoopen",
            "-mountpoint", mountPoint.toString(), dmg.toString(),
        )
        try {
            val bundle = mountPoint.listDirectoryEntries("*.app").firstOrNull { it.isDirectory() }
                ?: throw IOException("The disk image does not contain an app")
            run("ditto", bundle.toString(), staged.toString())
        } finally {
            if (runCatching { run("hdiutil", "detach", mountPoint.toString()) }.isFailure) {
                runCatching { run("hdiutil", "detach", "-force", mountPoint.toString()) }
                    .onFailure { stagingLog.warn(it) { "could not detach $mountPoint" } }
            }
        }
        // HttpClient downloads carry no quarantine flag today; strip it anyway so the relaunch never prompts.
        runCatching { run("xattr", "-dr", "com.apple.quarantine", staged.toString()) }

        if (!staged.resolve("Contents/MacOS/Tacit").isRegularFile()) throw IOException("The staged app has no launcher")
        val stagedId = bundleIdentifier(staged) ?: throw IOException("The staged app has no CFBundleIdentifier")
        val installedId = installedApp?.let(::bundleIdentifier)
        if (installedId != null && installedId != stagedId) {
            throw IOException("Bundle identifier mismatch: installed $installedId, update $stagedId")
        }
        return staged
    }

    /** Reads only; safe to call on the running install. */
    private fun bundleIdentifier(app: Path): String? = runCatching {
        run("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleIdentifier", app.resolve("Contents/Info.plist").toString())
            .trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun run(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw IOException("${command.first()} timed out")
        }
        if (process.exitValue() != 0) throw IOException("${command.joinToString(" ")} failed: ${output.trim()}")
        return output
    }

    fun deleteRecursively(path: Path) {
        if (!path.exists(LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
