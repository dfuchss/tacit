package org.fuchss.matrix.tacit.update

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.messenger.util.getAppPath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fuchss.matrix.tacit.BuildConfig
import org.fuchss.matrix.tacit.Flavor
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val updaterLog = Logger("org.fuchss.matrix.tacit.update.TacitUpdater")

private const val OLD_UPDATE_SUFFIX = ".old-update"

/**
 * Checks for a newer Tacit release, stages it (verified under `<app data>/updates/<tag>/`, ready app in `<install>.new-update`) and, after the app has exited
 * completely, hands over to [TacitUpdateHelper]. The running install is never modified. Fetching is done by a
 * [ReleaseSource], downloading and staging by [UpdateDownloader], persistence by [UpdateStore]; this class is the
 * state machine.
 *
 * Test overrides: `TACIT_UPDATE_API_URL` (release JSON endpoint), `TACIT_UPDATE_CURRENT_VERSION` (pretend to be
 * this version, also enables the updater on DEV/SNAPSHOT builds) and `TACIT_UPDATE_FORCE=1` (check right after
 * start and treat the latest release as an update even when it is not newer).
 */
class TacitUpdater internal constructor(
    private val currentVersion: TacitVersion?,
    private val install: TacitInstallLocation,
    private val asset: TacitUpdateAsset?,
    appDataDir: Path,
    private val source: ReleaseSource,
    private val force: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Test hook: builds the swap helper; the default writes and validates the real script. */
    private val prepareHelper: (TacitUpdateHelperParams) -> ProcessBuilder = TacitUpdateHelper::prepare,
) : TacitUpdateController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Serializes checks and the start-up clean-up; held during network I/O, so the store has its own lock. */
    private val checkMutex = Mutex()
    private val store = UpdateStore(appDataDir)
    private val downloader = UpdateDownloader(source, store.updatesDir)

    @Volatile
    private var staged: TacitStagedUpdate? = null

    private val _state = MutableStateFlow<TacitUpdateState>(TacitUpdateState.Idle)
    override val state: StateFlow<TacitUpdateState> = _state.asStateFlow()

    private val _autoCheck = MutableStateFlow(store.current.autoCheck)
    override val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override val releaseUrl: String
        get() = (_state.value as? TacitUpdateState.ManualOnly)?.url ?: store.current.cachedRelease?.htmlUrl ?: RELEASES_PAGE

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
            cleanUp()
            delay(if (force) 3.seconds else 30.seconds)
            while (isActive) {
                checkIfDue()
                if (force) break
                delay(POLL_INTERVAL)
            }
        }
    }

    /** The start-up clean-up; internal so tests can run it without the background loop. */
    internal suspend fun cleanUp() = checkMutex.withLock { cleanUpAfterPreviousRun() }

    /** One tick of the background loop: checks when auto-check is on and the last check is old enough (or forced). */
    internal suspend fun checkIfDue() {
        val due = clock() - store.current.lastCheckEpochMs >= CHECK_INTERVAL.inWholeMilliseconds
        if (_autoCheck.value && (due || force)) check()
    }

    override fun setAutoCheck(enabled: Boolean) {
        _autoCheck.value = enabled
        scope.launch { store.update { it.copy(autoCheck = enabled) } }
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
            prepareHelper(helperParams(location, update))
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
        var notify = false
        // Check and set inside one store update, so concurrent callers cannot both see an unannounced version.
        store.update {
            notify = it.notifiedTag != version
            if (notify) it.copy(notifiedTag = version) else it
        }
        return notify
    }

    private fun helperParams(location: TacitInstallLocation.SelfUpdatable, update: TacitStagedUpdate) = TacitUpdateHelperParams(
        os = location.os,
        pid = ProcessHandle.current().pid(),
        installRoot = location.root,
        stagedRoot = update.stagedRoot,
        stagingDir = update.stagingDir,
        resultFile = store.resultFile,
        version = update.version.toString(),
    )

    /**
     * Called by `Main` after the app has shut down. Starts the prepared detached helper if a restart to update
     * was requested and returns whether the helper was started. Either way Tacit exits afterwards and is not started
     * again: when the helper cannot be started, the failure is recorded for the next start and the user opens Tacit.
     */
    fun launchPendingUpdate(): Boolean {
        val pending = pendingRestart ?: return false
        try {
            store.clearResult()
            pending.helper.start()
            updaterLog.info { "update helper started for ${pending.update.version}" }
            return true
        } catch (e: Exception) {
            updaterLog.error(e) { "could not start the update helper" }
            runCatching { store.writeFailure(pending.update.version.toString(), "could not start the update helper") }
        }
        return false
    }

    /** Runs one check; internal so tests can drive the state machine without the background loop. */
    internal suspend fun check() = checkMutex.withLock {
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
        store.update { it.copy(lastCheckEpochMs = clock()) }
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
        staged = downloader.stage(release, asset, assetUrl, install) { progress ->
            _state.value = TacitUpdateState.Downloading(version, progress)
        }
        store.update { it.copy(stagedTag = release.tag) }
        _state.value = TacitUpdateState.ReadyToRestart(version)
    }

    /** The ETag is only sent while its release is cached, so a "not modified" answer always has a release. */
    private fun fetchLatestRelease(): TacitRelease? {
        val settings = store.current
        val etag = settings.etag?.takeIf { settings.cachedRelease != null }
        return when (val fetch = source.fetchLatest(etag)) {
            ReleaseFetch.NotModified -> store.current.cachedRelease
            is ReleaseFetch.Fetched -> {
                store.update { it.copy(etag = fetch.etag, cachedRelease = fetch.release) }
                fetch.release
            }
        }
    }

    /**
     * Reports the outcome of the last swap, keeps a still valid staged update and removes everything else:
     * older staging folders, a leftover `<install>.old-update` and an unowned `<install>.new-update` next to (never inside) the install.
     * Never throws: a folder that is still in use is skipped.
     */
    private fun cleanUpAfterPreviousRun() {
        runCatching {
            val text = store.readResult()
            if (text != null) {
                updaterLog.info { "previous update result: $text" }
                _lastError.value = failureFromResult(text)
                store.clearResult()
            }
        }.onFailure { updaterLog.warn(it) { "could not read ${store.resultFile}" } }

        runCatching {
            val current = currentVersion
            val settings = store.current
            val keepTag = settings.stagedTag?.takeIf { tag -> isStagedUpdateUsable(tag, current, force) }
            settings.cachedRelease?.let { cached ->
                if (!force && (current == null || cached.version <= current)) {
                    store.update { it.copy(cachedRelease = null, etag = null) }
                }
            }
            val updatesDir = store.updatesDir
            if (updatesDir.isDirectory()) {
                updatesDir.listDirectoryEntries().filter { it.fileName.toString() != keepTag }.forEach { deleteTolerantly(it) }
            }
            val location = install as? TacitInstallLocation.SelfUpdatable
            if (keepTag != null && location != null) {
                downloader.existing(keepTag, location.root)?.let { kept ->
                    staged = kept
                    // Even after a failed swap the kept update is offered again, so the user can retry.
                    _state.value = readyOr(kept.version, _state.value)
                }
            }
            if (staged == null && store.current.stagedTag != null) store.update { it.copy(stagedTag = null) }

            if (location != null) {
                val leftover = location.root.resolveSibling("${location.root.fileName}$OLD_UPDATE_SUFFIX")
                if (leftover.exists()) deleteTolerantly(leftover)
                // A `<install>.new-update` that no kept update owns is from an abandoned or superseded download.
                if (staged == null) {
                    val newUpdate = TacitUpdateStaging.newUpdatePath(location.root)
                    listOf(newUpdate, newUpdate.resolveSibling("${newUpdate.fileName}.partial"))
                        .filter { it.exists() }.forEach(::deleteTolerantly)
                }
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

    companion object {
        private val CHECK_INTERVAL: Duration = 24.hours
        private val POLL_INTERVAL: Duration = 1.hours
        private const val DELETE_ATTEMPTS = 3

        /** [staged] wins over [fallback]: an already staged update must not be hidden by a check or its failure. */
        internal fun readyOr(staged: TacitVersion?, fallback: TacitUpdateState): TacitUpdateState =
            if (staged != null) TacitUpdateState.ReadyToRestart(staged.toString()) else fallback

        internal fun failureResult(version: String, message: String) = UpdateStore.failureResult(version, message)

        /** The message of a failed swap from the helper's result file, or `null` when it succeeded. */
        internal fun failureFromResult(text: String): String? = UpdateStore.failureFromResult(text)

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
                source = GitHubReleaseSource(
                    System.getenv("TACIT_UPDATE_API_URL")?.takeIf { it.isNotBlank() } ?: DEFAULT_RELEASE_API_URL
                ),
                force = System.getenv("TACIT_UPDATE_FORCE").let { it == "1" || it.equals("true", ignoreCase = true) },
            )
        }

        /** An updater that never checks or installs, regardless of flavor or test overrides (e.g. for the demo). */
        fun disabled(appDataDir: Path): TacitUpdater = TacitUpdater(
            currentVersion = null,
            install = TacitInstallLocation.ManualOnly("updater disabled"),
            asset = null,
            appDataDir = appDataDir,
            source = GitHubReleaseSource(),
            force = false,
        )
    }
}
