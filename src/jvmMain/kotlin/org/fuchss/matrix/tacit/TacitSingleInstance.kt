package org.fuchss.matrix.tacit

import org.fuchss.matrix.tacit.update.TacitOs
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists

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
    if (!usesSingleInstanceLock(os) || !appDataDir.resolve(SINGLE_INSTANCE_LOCK_FILE).exists()) return args
    return arrayOf(activationUri(appUri))
}

/**
 * Whether a running instance holds [lockFile]. Upstream's running instance keeps a shared lock on it, so an
 * exclusive lock cannot be taken then; a lock file left behind by a crashed instance is not held. Any error counts as
 * "not held", which keeps the normal behaviour.
 */
internal fun isSingleInstanceLockHeld(lockFile: Path): Boolean {
    if (!lockFile.exists()) return false
    return try {
        FileChannel.open(lockFile, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
            val lock = channel.tryLock() ?: return true
            lock.release()
            false
        }
    } catch (_: OverlappingFileLockException) {
        true // held within this JVM
    } catch (_: Exception) {
        false
    }
}

/** Main log of the running instance; read by `Main` before logging is set up. */
internal const val MAIN_LOGGING_CONFIG = "/lognity.json"

/** Log of a launch that only hands its arguments to the running instance and exits. */
internal const val LAUNCHER_LOGGING_CONFIG = "/lognity-launcher.json"

/**
 * The logging config for this launch. A launch that will only hand its arguments to an already running instance
 * logs to its own small file, so it no longer writes into (and clobbers) the running instance's log.
 */
internal fun loggingConfigResource(os: TacitOs?, appDataDir: Path): String =
    if (usesSingleInstanceLock(os) && isSingleInstanceLockHeld(appDataDir.resolve(SINGLE_INSTANCE_LOCK_FILE))) {
        LAUNCHER_LOGGING_CONFIG
    } else {
        MAIN_LOGGING_CONFIG
    }
