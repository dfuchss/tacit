package org.fuchss.matrix.tacit.update

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.messenger.util.getAppPath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.fuchss.matrix.tacit.BuildConfig
import org.fuchss.matrix.tacit.Flavor
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import kotlin.io.path.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val updaterLog = Logger("org.fuchss.matrix.tacit.update.TacitUpdater")

private const val DEFAULT_API_URL = "https://api.github.com/repos/dfuchss/tacit/releases/latest"
private const val RELEASES_PAGE = "https://github.com/dfuchss/tacit/releases/latest"
private const val SHA256SUMS = "SHA256SUMS"
private const val OLD_UPDATE_SUFFIX = ".old-update"

data class TacitRelease(
    val tag: String,
    val version: TacitVersion,
    val htmlUrl: String,
    val notes: String?,
    /** Asset name to download URL. */
    val assets: Map<String, String>,
) {
    companion object {
        fun parse(json: JsonObject): TacitRelease? {
            val tag = json["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
            val version = TacitVersion.parse(tag) ?: return null
            val assets = (json["assets"] as? JsonArray).orEmpty().mapNotNull { asset ->
                val obj = asset as? JsonObject ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                name to url
            }.toMap()
            return TacitRelease(
                tag = tag,
                version = version,
                htmlUrl = json["html_url"]?.jsonPrimitive?.contentOrNull ?: RELEASES_PAGE,
                notes = json["body"]?.jsonPrimitive?.contentOrNull,
                assets = assets,
            )
        }
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("tag_name", tag)
        put("html_url", htmlUrl)
        notes?.let { put("body", it) }
        putJsonArray("assets") {
            assets.forEach { (name, url) ->
                addJsonObject {
                    put("name", name)
                    put("browser_download_url", url)
                }
            }
        }
    }
}

/** Global per install (not per profile): `<app data>/tacit-updater.json`. */
private data class TacitUpdaterPersisted(
    val autoCheck: Boolean = true,
    val lastCheckEpochMs: Long = 0,
    val etag: String? = null,
    val cachedRelease: TacitRelease? = null,
    val stagedTag: String? = null,
    val notifiedTag: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("autoCheck", autoCheck)
        put("lastCheckEpochMs", lastCheckEpochMs)
        etag?.let { put("etag", it) }
        cachedRelease?.let { put("cachedRelease", it.toJson()) }
        stagedTag?.let { put("stagedTag", it) }
        notifiedTag?.let { put("notifiedTag", it) }
    }

    companion object {
        fun parse(json: JsonObject) = TacitUpdaterPersisted(
            autoCheck = json["autoCheck"]?.jsonPrimitive?.booleanOrNull ?: true,
            lastCheckEpochMs = json["lastCheckEpochMs"]?.jsonPrimitive?.longOrNull ?: 0,
            etag = json["etag"]?.jsonPrimitive?.contentOrNull,
            cachedRelease = (json["cachedRelease"] as? JsonObject)?.let(TacitRelease::parse),
            stagedTag = json["stagedTag"]?.jsonPrimitive?.contentOrNull,
            notifiedTag = json["notifiedTag"]?.jsonPrimitive?.contentOrNull,
        )
    }
}

private data class TacitStagedUpdate(val version: TacitVersion, val stagingDir: Path, val stagedRoot: Path)

/**
 * Checks GitHub for a newer Tacit release, stages it under `<app data>/updates/<tag>/` and, after the app has
 * exited completely, hands over to [TacitUpdateHelper]. The running install is never modified.
 *
 * Test overrides: `TACIT_UPDATE_API_URL` (release JSON endpoint), `TACIT_UPDATE_CURRENT_VERSION` (pretend to be
 * this version, also enables the updater on DEV/SNAPSHOT builds) and `TACIT_UPDATE_FORCE=1` (check right after
 * start and treat the latest release as an update even when it is not newer).
 */
class TacitUpdater private constructor(
    private val currentVersion: TacitVersion?,
    private val install: TacitInstallLocation,
    private val asset: TacitUpdateAsset?,
    private val appDataDir: Path,
    private val apiUrl: String,
    private val force: Boolean,
) : TacitUpdateController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Serializes checks and the start-up clean-up; held during network I/O, so never take it for persistence. */
    private val checkMutex = Mutex()
    private val persistLock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(CONNECT_TIMEOUT)
        .build()
    private val userAgent = "Tacit/${BuildConfig.version} (updater)"

    private val updatesDir: Path = appDataDir.resolve("updates")
    private val persistedFile: Path = appDataDir.resolve("tacit-updater.json")
    private val resultFile: Path = updatesDir.resolve("update-result.json")

    private var persisted: TacitUpdaterPersisted = loadPersisted()
    @Volatile
    private var staged: TacitStagedUpdate? = null

    private val _state = MutableStateFlow<TacitUpdateState>(TacitUpdateState.Idle)
    override val state: StateFlow<TacitUpdateState> = _state.asStateFlow()

    private val _autoCheck = MutableStateFlow(persisted.autoCheck)
    override val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override val releaseUrl: String
        get() = (_state.value as? TacitUpdateState.ManualOnly)?.url ?: persisted.cachedRelease?.htmlUrl ?: RELEASES_PAGE

    private val _exitRequests = MutableSharedFlow<Unit>(replay = 1)

    /** Emits when the app must exit completely for a restart to update; collected inside `application {}`. */
    val exitRequests: SharedFlow<Unit> = _exitRequests.asSharedFlow()

    /** Whether this build can update itself; the controller is only registered for the UI then. */
    val isEnabled: Boolean get() = currentVersion != null

    /** A restart whose helper is already prepared; [launchPendingUpdate] only has to start it. */
    private class PendingRestart(
        val update: TacitStagedUpdate,
        val location: TacitInstallLocation.SelfUpdatable,
        val helper: ProcessBuilder,
    )

    @Volatile
    private var pendingRestart: PendingRestart? = null

    fun start() {
        if (currentVersion == null) {
            updaterLog.info { "updater disabled for version ${BuildConfig.version}" }
            return
        }
        updaterLog.info { "updater enabled for $currentVersion, install: $install, asset: ${asset?.name}" }
        scope.launch {
            checkMutex.withLock { cleanUpAfterPreviousRun() }
            delay(if (force) 3.seconds else 30.seconds)
            while (isActive) {
                val due = System.currentTimeMillis() - persisted.lastCheckEpochMs >= 24.hours.inWholeMilliseconds
                if (_autoCheck.value && (due || force)) check()
                if (force) break
                delay(1.hours)
            }
        }
    }

    override fun setAutoCheck(enabled: Boolean) {
        _autoCheck.value = enabled
        scope.launch { updatePersisted { it.copy(autoCheck = enabled) } }
    }

    override fun checkNow() {
        if (currentVersion == null) return
        scope.launch { check() }
    }

    override fun restartToUpdate() {
        val update = staged ?: return
        if (_state.value !is TacitUpdateState.ReadyToRestart) return
        val location = install as? TacitInstallLocation.SelfUpdatable
        if (location == null) {
            _state.value = TacitUpdateState.Failed("This installation cannot be updated in place")
            return
        }
        updaterLog.info { "restart to update to ${update.version} requested" }
        // Prepare and validate the helper before exiting: if that fails, Tacit stays running.
        val helper = try {
            TacitUpdateHelper.prepare(helperParams(location, update))
        } catch (e: Exception) {
            updaterLog.error(e) { "could not prepare the update helper" }
            _state.value = TacitUpdateState.Failed(e.message ?: e::class.simpleName.orEmpty())
            return
        }
        pendingRestart = PendingRestart(update, location, helper)
        _exitRequests.tryEmit(Unit)
    }

    /** Whether the tray should still announce [version]; marks it as announced. */
    fun shouldNotify(version: String): Boolean {
        if (synchronized(persistLock) { persisted.notifiedTag } == version) return false
        scope.launch { updatePersisted { it.copy(notifiedTag = version) } }
        return true
    }

    private fun helperParams(location: TacitInstallLocation.SelfUpdatable, update: TacitStagedUpdate) = TacitUpdateHelperParams(
        os = location.os,
        pid = ProcessHandle.current().pid(),
        installRoot = location.root,
        stagedRoot = update.stagedRoot,
        stagingDir = update.stagingDir,
        resultFile = resultFile,
        version = update.version.toString(),
    )

    /**
     * Called by `Main` after the app has shut down. Starts the prepared detached helper if a restart to update
     * was requested and returns whether the caller must exit the process right away. When the helper cannot be
     * started, the failure is recorded for the next start and the current install is launched again.
     */
    fun launchPendingUpdate(): Boolean {
        val pending = pendingRestart ?: return false
        try {
            Files.deleteIfExists(resultFile)
            pending.helper.start()
            updaterLog.info { "update helper started for ${pending.update.version}" }
            return true
        } catch (e: Exception) {
            updaterLog.error(e) { "could not start the update helper" }
            runCatching {
                Files.createDirectories(updatesDir)
                resultFile.writeText(failureResult(pending.update.version.toString(), "could not start the update helper"))
            }
        }
        return runCatching { TacitUpdateHelper.relaunch(pending.location.os, pending.location.root) }
            .onFailure { updaterLog.error(it) { "could not relaunch Tacit" } }
            .isSuccess
    }

    private suspend fun check() = checkMutex.withLock {
        val current = currentVersion ?: return@withLock
        // A staged update stays offered while checking, so the card does not flicker.
        _state.value = readyOr(staged?.version, TacitUpdateState.Checking)
        try {
            // Blocking network I/O; cancelling the scope interrupts it.
            runInterruptible { checkBlocking(current) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updaterLog.warn(e) { "update check failed" }
            // An already staged update is still offered; the failure is only logged then.
            _state.value = readyOr(staged?.version, TacitUpdateState.Failed(e.message ?: e::class.simpleName.orEmpty()))
        }
    }

    private fun checkBlocking(current: TacitVersion) {
        val release = fetchLatestRelease()
        updatePersisted { it.copy(lastCheckEpochMs = System.currentTimeMillis()) }
        if (release == null || (release.version <= current && !force)) {
            _state.value = readyOr(staged?.version, TacitUpdateState.UpToDate)
            return
        }
        val version = release.version.toString()
        val assetUrl = asset?.let { release.assets[it.name] }
        if (install !is TacitInstallLocation.SelfUpdatable || asset == null || assetUrl == null) {
            _state.value = TacitUpdateState.ManualOnly(version, release.htmlUrl)
            return
        }
        val existing = staged
        if (existing != null && existing.version == release.version && existing.stagedRoot.exists()) {
            _state.value = TacitUpdateState.ReadyToRestart(version)
            return
        }
        staged = stage(release, asset, assetUrl, install)
        updatePersisted { it.copy(stagedTag = release.tag) }
        _state.value = TacitUpdateState.ReadyToRestart(version)
    }

    private fun fetchLatestRelease(): TacitRelease? {
        val request = HttpRequest.newBuilder(URI(apiUrl))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .apply { if (persisted.cachedRelease != null) persisted.etag?.let { header("If-None-Match", it) } }
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            304 -> persisted.cachedRelease
            200 -> {
                val release = TacitRelease.parse(json.parseToJsonElement(response.body()).jsonObject)
                updatePersisted {
                    it.copy(etag = response.headers().firstValue("ETag").orElse(null), cachedRelease = release)
                }
                release
            }

            else -> throw IOException("GitHub answered ${response.statusCode()}")
        }
    }

    private fun stage(
        release: TacitRelease,
        asset: TacitUpdateAsset,
        assetUrl: String,
        location: TacitInstallLocation.SelfUpdatable,
    ): TacitStagedUpdate {
        val sumsUrl = release.assets[SHA256SUMS] ?: throw IOException("The release has no $SHA256SUMS")
        val expected = TacitSha256Sums.parse(downloadString(sumsUrl))[asset.name]
            ?: throw IOException("$SHA256SUMS has no entry for ${asset.name}")

        val stagingDir = updatesDir.resolve(release.tag)
        if (stagingDir.exists()) TacitUpdateStaging.deleteRecursively(stagingDir)
        Files.createDirectories(stagingDir)

        val target = stagingDir.resolve(asset.name)
        val actual = download(assetUrl, target, release.version.toString())
        if (!actual.equals(expected, ignoreCase = true)) {
            Files.deleteIfExists(target)
            throw IOException("Checksum mismatch for ${asset.name}")
        }
        val stagedRoot = when (asset.packageType) {
            TacitUpdatePackage.DMG -> TacitUpdateStaging.stageDmg(target, stagingDir, location.root)
            TacitUpdatePackage.ZIP -> TacitUpdateStaging.stageZip(location.os, target, stagingDir)
        }
        Files.deleteIfExists(target)
        updaterLog.info { "staged ${release.tag} at $stagedRoot" }
        return TacitStagedUpdate(release.version, stagingDir, stagedRoot)
    }

    private fun downloadString(url: String): String {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", userAgent)
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw IOException("Download of $url answered ${response.statusCode()}")
        return response.body()
    }

    /** Downloads to `<target>.part`, then renames it; returns the SHA-256 hex digest. */
    private fun download(url: String, target: Path, version: String): String {
        val part = target.resolveSibling("${target.fileName}.part")
        // The request timeout only covers the response headers; the body loop below checks for cancellation.
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", userAgent)
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("Download of $url answered ${response.statusCode()}")
        }
        val total = response.headers().firstValueAsLong("Content-Length").orElse(-1)
        val digest = MessageDigest.getInstance("SHA-256")
        _state.value = TacitUpdateState.Downloading(version, if (total > 0) 0f else null)
        response.body().use { input ->
            Files.newOutputStream(part).use { output ->
                val buffer = ByteArray(64 * 1024)
                var read = 0L
                var lastReported = 0L
                while (true) {
                    if (Thread.interrupted()) throw InterruptedException("download cancelled")
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    read += n
                    if (total > 0 && read - lastReported >= 512 * 1024) {
                        lastReported = read
                        _state.value = TacitUpdateState.Downloading(version, (read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Reports the outcome of the last swap, keeps a still valid staged update and removes everything else:
     * older staging folders and a leftover `<install>.old-update` next to (never inside) the install.
     * Never throws: a folder that is still in use is skipped.
     */
    private fun cleanUpAfterPreviousRun() {
        runCatching {
            if (resultFile.exists()) {
                val text = resultFile.readText()
                updaterLog.info { "previous update result: $text" }
                _lastError.value = failureFromResult(text)
                Files.deleteIfExists(resultFile)
            }
        }.onFailure { updaterLog.warn(it) { "could not read $resultFile" } }

        runCatching {
            val current = currentVersion
            val keepTag = persisted.stagedTag?.takeIf { tag -> isStagedUpdateUsable(tag, current, force) }
            persisted.cachedRelease?.let { cached ->
                if (!force && (current == null || cached.version <= current)) {
                    updatePersisted { it.copy(cachedRelease = null, etag = null) }
                }
            }
            if (updatesDir.isDirectory()) {
                updatesDir.listDirectoryEntries().filter { it.fileName.toString() != keepTag }.forEach { deleteTolerantly(it) }
            }
            val location = install as? TacitInstallLocation.SelfUpdatable
            if (keepTag != null && location != null) {
                val stagingDir = updatesDir.resolve(keepTag)
                val stagedRoot = when (location.os) {
                    TacitOs.MACOS -> stagingDir.resolve("Tacit.app")
                    else -> stagingDir.resolve("extracted").resolve("Tacit")
                }
                val version = TacitVersion.parse(keepTag)
                if (version != null && stagedRoot.isDirectory()) {
                    staged = TacitStagedUpdate(version, stagingDir, stagedRoot)
                    // Even after a failed swap the kept update is offered again, so the user can retry.
                    _state.value = readyOr(version, _state.value)
                }
            }
            if (staged == null && persisted.stagedTag != null) updatePersisted { it.copy(stagedTag = null) }

            if (location != null) {
                val leftover = location.root.resolveSibling("${location.root.fileName}$OLD_UPDATE_SUFFIX")
                if (leftover.exists()) deleteTolerantly(leftover)
            }
        }.onFailure { updaterLog.warn(it) { "clean-up after the previous run failed" } }
    }

    /** Folders can stay locked for a moment (the helper or a scanner); retry a few times, then give up. */
    private fun deleteTolerantly(path: Path) {
        repeat(DELETE_ATTEMPTS) { attempt ->
            val failure = runCatching { TacitUpdateStaging.deleteRecursively(path) }.exceptionOrNull() ?: return
            if (attempt == DELETE_ATTEMPTS - 1) updaterLog.warn(failure) { "could not remove $path" }
            else Thread.sleep(1000)
        }
    }

    private fun loadPersisted(): TacitUpdaterPersisted = runCatching {
        if (!persistedFile.exists()) TacitUpdaterPersisted()
        else TacitUpdaterPersisted.parse(json.parseToJsonElement(persistedFile.readText()).jsonObject)
    }.getOrElse {
        updaterLog.warn(it) { "could not read $persistedFile" }
        TacitUpdaterPersisted()
    }

    /** Persistence has its own lock, so it never waits for a running check. */
    private fun updatePersisted(change: (TacitUpdaterPersisted) -> TacitUpdaterPersisted) = synchronized(persistLock) {
        val value = change(persisted)
        persisted = value
        runCatching {
            Files.createDirectories(appDataDir)
            val tmp = persistedFile.resolveSibling("${persistedFile.fileName}.tmp")
            tmp.writeText(value.toJson().toString())
            Files.move(tmp, persistedFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { updaterLog.warn(it) { "could not write $persistedFile" } }
    }

    companion object {
        private val CONNECT_TIMEOUT = Duration.ofSeconds(20)
        private val REQUEST_TIMEOUT = Duration.ofSeconds(30)
        private const val DELETE_ATTEMPTS = 3

        /** [staged] wins over [fallback]: an already staged update must not be hidden by a check or its failure. */
        internal fun readyOr(staged: TacitVersion?, fallback: TacitUpdateState): TacitUpdateState =
            if (staged != null) TacitUpdateState.ReadyToRestart(staged.toString()) else fallback

        internal fun failureResult(version: String, message: String) =
            buildJsonObject { put("success", false); put("version", version); put("message", message) }.toString()

        /** The message of a failed swap from the helper's result file, or `null` when it succeeded. */
        internal fun failureFromResult(text: String): String? {
            val result = Json.parseToJsonElement(text.trimStart('\uFEFF')).jsonObject
            if (result["success"]?.jsonPrimitive?.booleanOrNull == true) return null
            val message = result["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val version = result["version"]?.jsonPrimitive?.contentOrNull.orEmpty()
            return "$version: $message"
        }

        /** A staged update is only worth offering when it is strictly newer than what runs now (or when forced). */
        internal fun isStagedUpdateUsable(tag: String, current: TacitVersion?, force: Boolean): Boolean {
            val version = TacitVersion.parse(tag) ?: return false
            return current != null && (force || version > current)
        }

        fun create(): TacitUpdater {
            val override = System.getenv("TACIT_UPDATE_CURRENT_VERSION")?.takeIf { it.isNotBlank() }
            val currentVersion = when {
                override != null -> TacitVersion.parse(override)
                BuildConfig.flavor == Flavor.DEV -> null
                else -> TacitVersion.parse(BuildConfig.version)
            }
            return TacitUpdater(
                currentVersion = currentVersion,
                install = TacitInstallLocation.current(),
                asset = TacitUpdateAssets.forPlatform(TacitOs.current, System.getProperty("os.arch").orEmpty()),
                appDataDir = Paths.get(getAppPath(BuildConfig.appId).toString()),
                apiUrl = System.getenv("TACIT_UPDATE_API_URL")?.takeIf { it.isNotBlank() } ?: DEFAULT_API_URL,
                force = System.getenv("TACIT_UPDATE_FORCE").let { it == "1" || it.equals("true", ignoreCase = true) },
            )
        }
    }
}
