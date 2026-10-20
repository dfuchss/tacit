package org.fuchss.matrix.tacit

import org.fuchss.matrix.tacit.update.TacitOs
import java.nio.file.Path
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
