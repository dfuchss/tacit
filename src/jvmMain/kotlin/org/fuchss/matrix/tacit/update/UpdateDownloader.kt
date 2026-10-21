package org.fuchss.matrix.tacit.update

import de.connect2x.lognity.api.logger.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

private val downloaderLog = Logger("org.fuchss.matrix.tacit.update.UpdateDownloader")

private const val SHA256SUMS = "SHA256SUMS"

/**
 * A verified update: [stagedRoot] is `<install>.new-update` next to the install and replaces it after Tacit exited;
 * [stagingDir] (`<app data>/updates/<tag>/`) only holds the helper script, its log and the result.
 */
internal data class TacitStagedUpdate(val version: TacitVersion, val stagingDir: Path, val stagedRoot: Path)

/**
 * Downloads a release asset, verifies it against the release's `SHA256SUMS` and stages it with
 * [TacitUpdateStaging] under [updatesDir] and moves the ready app next to the install. Never writes inside the install.
 */
internal class UpdateDownloader(private val source: ReleaseSource, private val updatesDir: Path) {
    /**
     * Blocking; checks for thread interruption while downloading. [onProgress] gets `0f..1f`, or `null` when the
     * size is unknown, starting right before the first byte.
     */
    fun stage(
        release: TacitRelease,
        asset: TacitUpdateAsset,
        assetUrl: String,
        location: TacitInstallLocation.SelfUpdatable,
        onProgress: (Float?) -> Unit,
    ): TacitStagedUpdate {
        val sumsUrl = release.assets[SHA256SUMS] ?: throw IOException("The release has no $SHA256SUMS")
        val expected = TacitSha256Sums.parse(downloadString(sumsUrl))[asset.name]
            ?: throw IOException("$SHA256SUMS has no entry for ${asset.name}")

        val stagingDir = stagingDir(release.tag)
        if (stagingDir.exists()) TacitUpdateStaging.deleteRecursively(stagingDir)
        Files.createDirectories(stagingDir)

        val target = stagingDir.resolve(asset.name)
        val actual = download(assetUrl, target, onProgress)
        if (!actual.equals(expected, ignoreCase = true)) {
            Files.deleteIfExists(target)
            throw IOException("Checksum mismatch for ${asset.name}")
        }
        val prepared = when (asset.packageType) {
            TacitUpdatePackage.DMG -> TacitUpdateStaging.stageDmg(target, stagingDir, location.root)
            TacitUpdatePackage.ZIP -> TacitUpdateStaging.stageZip(location.os, target, stagingDir)
        }
        Files.deleteIfExists(target)
        // Ahead of time, so the swap after quitting is only renames on the install's volume.
        val stagedRoot = TacitUpdateStaging.placeNextToInstall(location.os, prepared, location.root)
        TacitUpdateStaging.deleteRecursively(stagingDir.resolve("extracted"))
        TacitUpdateStaging.deleteRecursively(stagingDir.resolve("mnt"))
        downloaderLog.info { "staged ${release.tag} at $stagedRoot" }
        return TacitStagedUpdate(release.version, stagingDir, stagedRoot)
    }

    /** The update staged for [tag] by an earlier run, or `null` when it is not (completely) there. */
    fun existing(tag: String, installRoot: Path): TacitStagedUpdate? {
        val version = TacitVersion.parse(tag) ?: return null
        val stagingDir = stagingDir(tag)
        val stagedRoot = TacitUpdateStaging.newUpdatePath(installRoot)
        return if (stagedRoot.isDirectory() && stagingDir.isDirectory()) TacitStagedUpdate(version, stagingDir, stagedRoot) else null
    }

    private fun stagingDir(tag: String): Path = updatesDir.resolve(tag)

    private fun downloadString(url: String): String =
        source.open(url).use { it.input.readBytes().toString(Charsets.UTF_8) }

    /** Downloads to `<target>.part`, then renames it; returns the SHA-256 hex digest. */
    private fun download(url: String, target: Path, onProgress: (Float?) -> Unit): String {
        val part = target.resolveSibling("${target.fileName}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        source.open(url).use { download ->
            val total = download.contentLength
            onProgress(if (total > 0) 0f else null)
            Files.newOutputStream(part).use { output ->
                val buffer = ByteArray(64 * 1024)
                var read = 0L
                var lastReported = 0L
                while (true) {
                    if (Thread.interrupted()) throw InterruptedException("download cancelled")
                    val n = download.input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    read += n
                    if (total > 0 && read - lastReported >= 512 * 1024) {
                        lastReported = read
                        onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
