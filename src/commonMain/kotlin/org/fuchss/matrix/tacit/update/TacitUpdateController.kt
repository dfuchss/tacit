package org.fuchss.matrix.tacit.update

import kotlinx.coroutines.flow.StateFlow

/** The state of the desktop self-updater, as shown in the settings, the banner and the tray. */
sealed interface TacitUpdateState {
    data object Idle : TacitUpdateState

    data object Checking : TacitUpdateState

    data object UpToDate : TacitUpdateState

    /** [progress] is in `0f..1f`, or `null` while the size is unknown. */
    data class Downloading(val version: String, val progress: Float?) : TacitUpdateState

    /** The update is staged outside the install; it is applied after Tacit has exited completely. */
    data class ReadyToRestart(val version: String) : TacitUpdateState

    /** A newer version exists, but this installation cannot be replaced in place (see README). */
    data class ManualOnly(val version: String, val url: String) : TacitUpdateState

    data class Failed(val message: String) : TacitUpdateState
}

/**
 * Desktop only; resolve it with `getOrNull()` so that platforms and builds without an updater (development or
 * snapshot versions) show nothing.
 */
interface TacitUpdateController {
    val state: StateFlow<TacitUpdateState>
    val autoCheck: StateFlow<Boolean>

    /** Why the last swap failed (reported by the next start); independent of [state], so a staged update can be retried. */
    val lastError: StateFlow<String?>

    /** The release page to show for "What's new" or a manual download; open it with `LocalUriHandler`. */
    val releaseUrl: String

    fun setAutoCheck(enabled: Boolean)
    fun checkNow()

    /** Exits Tacit completely (tray included) and lets the detached helper apply the staged update. */
    fun restartToUpdate()
}
