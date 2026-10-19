package org.fuchss.matrix.tacit.update

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drives the updater's state machine against a fake [ReleaseSource]; nothing here touches the network. */
class TacitUpdaterFlowTest {
    private val tempDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanUpTempDirs() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
        tempDirs.clear()
    }

    private val current = TacitVersion.parse("26.9.2-tacit.2")!!
    private val asset = TacitUpdateAsset("tacit-linux-x64.zip", TacitUpdatePackage.ZIP)
    private val base: Path = Files.createTempDirectory("tacit-updater").also(tempDirs::add)
    private val appData: Path = base.resolve("app data")
    private val install = TacitInstallLocation.SelfUpdatable(TacitOs.LINUX, base.resolve("install/Tacit"))

    private class FakeSource(var latest: () -> ReleaseFetch) : ReleaseSource {
        val files = mutableMapOf<String, ByteArray>()
        val sentEtags = mutableListOf<String?>()
        val opened = mutableListOf<String>()

        override fun fetchLatest(etag: String?): ReleaseFetch {
            sentEtags += etag
            return latest()
        }

        override fun open(url: String): ReleaseDownload {
            opened += url
            val bytes = files[url] ?: throw IOException("Download of $url answered 404")
            return ReleaseDownload(ByteArrayInputStream(bytes), bytes.size.toLong())
        }
    }

    private fun release(tag: String) = TacitRelease(
        tag = tag,
        version = TacitVersion.parse(tag)!!,
        htmlUrl = "https://example.org/$tag",
        notes = null,
        assets = mapOf(
            asset.name to "https://example.org/$tag/${asset.name}",
            "SHA256SUMS" to "https://example.org/$tag/SHA256SUMS",
        ),
    )

    private fun linuxZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            mapOf("Tacit/bin/Tacit" to "#!/bin/sh\n", "Tacit/lib/app/Tacit.cfg" to "cfg").forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Serves [release] with a valid zip; [sumsFor] decides what `SHA256SUMS` claims for it. */
    private fun FakeSource.serve(release: TacitRelease, sumsFor: (ByteArray) -> String = ::sha256) {
        val zip = linuxZip()
        files[release.assets.getValue(asset.name)] = zip
        files[release.assets.getValue("SHA256SUMS")] = "${sumsFor(zip)}  ${asset.name}\n".toByteArray()
    }

    private fun updater(
        source: ReleaseSource,
        location: TacitInstallLocation = install,
        updateAsset: TacitUpdateAsset? = asset,
        clock: () -> Long = System::currentTimeMillis,
    ) = TacitUpdater(current, location, updateAsset, appData, source, force = false, clock = clock)

    @Test
    fun `no update when the latest release is not newer`() = runBlocking {
        val source = FakeSource { ReleaseFetch.Fetched(release("v26.9.2-tacit.2"), "\"e1\"") }
        val updater = updater(source)
        updater.check()
        assertEquals(TacitUpdateState.UpToDate, updater.state.value)
        assertTrue(source.opened.isEmpty())
    }

    @Test
    fun `a newer release is downloaded, verified and staged outside the install`() = runBlocking {
        val newer = release("v26.9.2-tacit.3")
        val source = FakeSource { ReleaseFetch.Fetched(newer, "\"e1\"") }.apply { serve(newer) }
        val updater = updater(source)
        updater.check()
        assertEquals(TacitUpdateState.ReadyToRestart("26.9.2-tacit.3"), updater.state.value)
        val stagedRoot = TacitUpdateStaging.stagedRoot(TacitOs.LINUX, appData.resolve("updates/v26.9.2-tacit.3"))
        assertTrue(stagedRoot.resolve("bin/Tacit").exists())
        assertFalse(appData.resolve("updates/v26.9.2-tacit.3/${asset.name}").exists(), "the archive is removed")
        assertFalse(install.root.exists(), "the install is never touched")

        // The next start keeps and offers the staged update without downloading again.
        val restarted = updater(FakeSource { error("no check expected") })
        restarted.cleanUp()
        assertEquals(TacitUpdateState.ReadyToRestart("26.9.2-tacit.3"), restarted.state.value)
        assertTrue(stagedRoot.isDirectory())
    }

    @Test
    fun `a checksum mismatch fails and stages nothing`() = runBlocking {
        val newer = release("v26.9.2-tacit.3")
        val source = FakeSource { ReleaseFetch.Fetched(newer, null) }.apply { serve(newer) { "0".repeat(64) } }
        val updater = updater(source)
        updater.check()
        assertEquals(TacitUpdateState.Failed("Checksum mismatch for ${asset.name}"), updater.state.value)
        val stagingDir = appData.resolve("updates/v26.9.2-tacit.3")
        assertFalse(TacitUpdateStaging.stagedRoot(TacitOs.LINUX, stagingDir).exists())
        assertFalse(stagingDir.resolve(asset.name).exists())

        // Nothing usable is kept for the next start.
        val restarted = updater(FakeSource { error("no check expected") })
        restarted.cleanUp()
        assertEquals(TacitUpdateState.Idle, restarted.state.value)
    }

    @Test
    fun `not modified reuses the cached release and its etag`() = runBlocking {
        val newer = release("v26.9.2-tacit.3")
        val source = FakeSource { ReleaseFetch.Fetched(newer, "\"e1\"") }
        // Not self-updatable: the release is only offered for a manual download.
        val updater = updater(source, location = TacitInstallLocation.ManualOnly("test"), updateAsset = null)
        updater.check()
        assertEquals(TacitUpdateState.ManualOnly("26.9.2-tacit.3", newer.htmlUrl), updater.state.value)

        source.latest = { ReleaseFetch.NotModified }
        // A new instance proves that the etag and the release were persisted.
        val again = updater(source, location = TacitInstallLocation.ManualOnly("test"), updateAsset = null)
        again.check()
        assertEquals(listOf(null, "\"e1\""), source.sentEtags)
        assertEquals(TacitUpdateState.ManualOnly("26.9.2-tacit.3", newer.htmlUrl), again.state.value)
        assertEquals(newer.htmlUrl, again.releaseUrl)
        assertTrue(source.opened.isEmpty())
    }

    @Test
    fun `no etag is sent without a cached release`() = runBlocking {
        Files.createDirectories(appData)
        appData.resolve("tacit-updater.json").writeText("""{"autoCheck":true,"lastCheckEpochMs":0,"etag":"\"stale\""}""")
        val source = FakeSource { ReleaseFetch.Fetched(null, "\"e2\"") }
        updater(source).check()
        assertEquals(listOf<String?>(null), source.sentEtags)
    }

    @Test
    fun `failed fetch reports the error`() = runBlocking {
        val updater = updater(FakeSource { throw IOException("GitHub answered 500") })
        updater.check()
        assertEquals(TacitUpdateState.Failed("GitHub answered 500"), updater.state.value)
    }

    @Test
    fun `auto-check off never checks in the background`() = runBlocking {
        Files.createDirectories(appData)
        appData.resolve("tacit-updater.json").writeText("""{"autoCheck":false,"lastCheckEpochMs":0}""")
        val source = FakeSource { ReleaseFetch.Fetched(release("v26.9.2-tacit.2"), null) }
        val updater = updater(source)
        assertFalse(updater.autoCheck.value)
        updater.checkIfDue()
        assertTrue(source.sentEtags.isEmpty())
        assertEquals(TacitUpdateState.Idle, updater.state.value)
    }

    @Test
    fun `background checks run at most once per interval`() = runBlocking {
        val now = 1_000_000_000_000L
        val source = FakeSource { ReleaseFetch.Fetched(release("v26.9.2-tacit.2"), null) }
        var time = now
        val updater = updater(source, clock = { time })
        updater.checkIfDue()
        assertEquals(1, source.sentEtags.size)
        time = now + 23 * 3_600_000L
        updater.checkIfDue()
        assertEquals(1, source.sentEtags.size)
        time = now + 24 * 3_600_000L
        updater.checkIfDue()
        assertEquals(2, source.sentEtags.size)
    }

    @Test
    fun `settings file keeps its format`() = runBlocking {
        val newer = release("v26.9.2-tacit.3")
        val updater = updater(FakeSource { ReleaseFetch.Fetched(newer, "\"e1\"") }, TacitInstallLocation.ManualOnly("test"), null)
        updater.check()
        val text = appData.resolve("tacit-updater.json").readText()
        listOf(""""autoCheck":true""", """"etag":"\"e1\""""", """"tag_name":"v26.9.2-tacit.3"""", """"browser_download_url":""")
            .forEach { assertTrue(it in text, "$it in $text") }
        assertFalse("stagedTag" in text, "absent values are omitted")
    }

    @Test
    fun `a malformed settings field only resets that field`() {
        Files.createDirectories(appData)
        appData.resolve("tacit-updater.json")
            .writeText("""{"autoCheck":false,"lastCheckEpochMs":{"bad":1},"cachedRelease":[],"notifiedTag":"v26.9.2-tacit.3"}""")
        val updater = updater(FakeSource { error("no check expected") })
        assertFalse(updater.autoCheck.value)
        assertFalse(updater.shouldNotify("v26.9.2-tacit.3"))
        assertEquals(RELEASES_PAGE, updater.releaseUrl)
    }

    @Test
    fun `github release json is parsed leniently`() {
        val release = GitHubReleaseSource.parseRelease(
            """{"tag_name":"v26.9.2-tacit.4","html_url":null,"body":"notes","extra":1,
               "assets":[{"name":"a.zip","browser_download_url":"https://x/a.zip","size":3},{"name":"broken"}]}"""
        )!!
        assertEquals(TacitVersion.parse("26.9.2-tacit.4"), release.version)
        assertEquals(RELEASES_PAGE, release.htmlUrl)
        assertEquals(mapOf("a.zip" to "https://x/a.zip"), release.assets)
        assertNull(GitHubReleaseSource.parseRelease("""{"tag_name":"v1.2.3-SNAPSHOT"}"""))
    }
}
