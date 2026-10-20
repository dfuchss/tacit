package org.fuchss.matrix.tacit

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val shutdownLog: Logger = Logger("org.fuchss.matrix.tacit.TacitShutdown")

/** Longest wait for the sync loops to report `STOPPED`; a running retry back-off alone can take 5 s. */
internal val SYNC_STOP_TIMEOUT: Duration = 15.seconds

/** Longest wait for the messenger's coroutines to finish after it was closed. */
internal val CLOSE_TIMEOUT: Duration = 15.seconds

/**
 * Shuts the messenger down without ever hanging the process.
 *
 * Upstream's `MainViewModel` stops the sync from a `NonCancellable` coroutine when the lifecycle stops, and
 * trixnity's `SyncApiClient.stop()` waits until the sync loop reports `STOPPED`. Only the loop itself sets that state.
 * If `close()` cancels the loop first (it was processing a response or waiting in its error back-off), the state never
 * changes, the non-cancellable coroutine waits forever and so does `closeSuspending()`. So [stopSyncs] runs first,
 * while the loops are still alive, and both steps are bounded: a timeout is logged and the shutdown goes on.
 *
 * Returns whether both steps finished in time.
 */
internal suspend fun shutDownGracefully(
    stopSyncs: List<suspend () -> Unit>,
    close: suspend () -> Unit,
    syncStopTimeout: Duration = SYNC_STOP_TIMEOUT,
    closeTimeout: Duration = CLOSE_TIMEOUT,
): Boolean {
    val syncsStopped = withTimeoutOrNull(syncStopTimeout) {
        coroutineScope { stopSyncs.map { stop -> async { runCatching { stop() } } }.awaitAll() }
    } != null
    if (!syncsStopped) shutdownLog.warn { "sync did not stop within $syncStopTimeout, closing anyway" }
    val closed = withTimeoutOrNull(closeTimeout) { close() } != null
    if (!closed) shutdownLog.warn { "messenger did not close within $closeTimeout, exiting anyway" }
    return syncsStopped && closed
}

/** [shutDownGracefully] for the active profile's Matrix clients and [matrixMultiMessenger] itself. */
internal suspend fun shutDownGracefully(matrixMultiMessenger: MatrixMultiMessenger): Boolean {
    val clients = matrixMultiMessenger.activeMatrixMessenger.value?.di?.getOrNull<MatrixClients>()?.value?.values.orEmpty()
    return shutDownGracefully(
        stopSyncs = clients.map { client -> suspend { client.stopSync() } },
        close = matrixMultiMessenger::closeSuspending,
    )
}
