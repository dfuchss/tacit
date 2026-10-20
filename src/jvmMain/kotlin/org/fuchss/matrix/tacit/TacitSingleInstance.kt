package org.fuchss.matrix.tacit

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.warn
import org.fuchss.matrix.tacit.update.TacitOs
import org.fuchss.matrix.tacit.update.TacitUpdateHelper
import java.nio.file.Path
import kotlin.io.path.exists

private val singleInstanceLog: Logger = Logger("org.fuchss.matrix.tacit.TacitSingleInstance")

/** Name of upstream's single-instance lock file in the app data directory; it holds the running instance's port. */
internal const val SINGLE_INSTANCE_LOCK_FILE = "port.lock"

/** Path of the URI that asks a running Tacit to show its window; no upstream URI handler reacts to it. */
internal const val ACTIVATION_URI_PATH = "tacit-activate"

/** URI a bare relaunch forwards to the running instance, e.g. `org.fuchss.matrix.tacit:/tacit-activate`. */
internal fun activationUri(appUri: String): String = "$appUri/$ACTIVATION_URI_PATH"

/** Whether upstream hands start arguments to an already running instance (Windows and Linux; macOS does that itself). */
internal fun usesSingleInstanceLock(os: TacitOs?): Boolean = os == TacitOs.WINDOWS || os == TacitOs.LINUX

/**
 * The arguments for upstream's URI handler. A launch without a URI while another instance holds the lock would
 * forward an empty string, which the running instance drops, so clicking the launcher of a Tacit hidden in the
 * tray did nothing. Such a launch forwards [activationUri] instead; every URI makes the running instance show its
 * window. Launches with a URI and first instances keep their arguments.
 */
internal fun singleInstanceArgs(args: Array<String>, appUri: String, os: TacitOs?, appDataDir: Path): Array<String> {
    if (!args.firstOrNull().isNullOrBlank()) return args
    if (!usesSingleInstanceLock(os) || !singleInstanceLockExists(appDataDir.resolve(SINGLE_INSTANCE_LOCK_FILE))) return args
    return arrayOf(activationUri(appUri))
}

/**
 * Whether [lockFile] exists, i.e. upstream's URI handler will hand this launch over to the instance named in it.
 *
 * This deliberately neither locks nor reads nor connects: upstream's running instance keeps a shared lock on the file,
 * a starting primary writes and locks it, and a concurrent launch reads it. Any lock taken here, even briefly, can
 * make those fail (mandatory locks on Windows, a failed shared `tryLock` on Linux). The price: a lock file left behind
 * by a crashed instance also counts, see [loggingConfigResource].
 */
internal fun singleInstanceLockExists(lockFile: Path): Boolean = runCatching { lockFile.exists() }.getOrDefault(false)

/** Main log of the running instance; read by `Main` before logging is set up. */
internal const val MAIN_LOGGING_CONFIG = "/lognity.json"

/** Log of a launch that only hands its arguments to the running instance and exits. */
internal const val LAUNCHER_LOGGING_CONFIG = "/lognity-launcher.json"

/** The logging config resource for this launch and why it was chosen, logged once logging is set up. */
internal data class LoggingConfigChoice(val resource: String, val reason: String)

/**
 * The logging config for this launch. A launch that will only hand its arguments to an already running instance
 * logs to its own small file, so it no longer writes into (and clobbers) the running instance's log.
 *
 * Edge case: the decision is made before upstream's handshake and only by the existence of `port.lock` (see
 * [singleInstanceLockExists]). A crashed instance leaves the file behind, the next launch then cannot reach it and
 * becomes the running instance itself, but keeps logging to `logs/launcher.log` for that whole session. The line
 * `Main` logs at start-up with [LoggingConfigChoice.reason] makes such a session traceable there; the next start
 * logs to the main log again because a clean exit removes the lock file.
 */
internal fun loggingConfigChoice(os: TacitOs?, appDataDir: Path): LoggingConfigChoice = when {
    !usesSingleInstanceLock(os) -> LoggingConfigChoice(MAIN_LOGGING_CONFIG, "no single-instance handover on $os")
    singleInstanceLockExists(appDataDir.resolve(SINGLE_INSTANCE_LOCK_FILE)) -> LoggingConfigChoice(
        LAUNCHER_LOGGING_CONFIG,
        "$SINGLE_INSTANCE_LOCK_FILE exists, expecting to hand over to the running instance " +
            "(if this session keeps running, that instance had crashed and left the file behind)",
    )

    else -> LoggingConfigChoice(MAIN_LOGGING_CONFIG, "no $SINGLE_INSTANCE_LOCK_FILE, this is the running instance")
}

/** The resource of [loggingConfigChoice]. */
internal fun loggingConfigResource(os: TacitOs?, appDataDir: Path): String = loggingConfigChoice(os, appDataDir).resource

/**
 * Starts the launcher at [appPath] (`jpackage.app-path`) again once this process has exited, for a launch that the
 * shutting-down instance swallowed (see [runThenShutDown]). Windows and Linux only; without a packaged launcher (a
 * development run) nothing is started.
 */
internal fun relaunchAfterExit(os: TacitOs?, appPath: String?) {
    if (os == null || !usesSingleInstanceLock(os)) return
    if (appPath.isNullOrBlank()) {
        singleInstanceLog.warn { "launched again while shutting down, but not running from a packaged app" }
        return
    }
    singleInstanceLog.info { "launched again while shutting down, starting $appPath after exit" }
    runCatching { TacitUpdateHelper.relaunchAfterExit(os, ProcessHandle.current().pid(), listOf(appPath)) }
        .onFailure { singleInstanceLog.error(it) { "could not start Tacit again" } }
}
