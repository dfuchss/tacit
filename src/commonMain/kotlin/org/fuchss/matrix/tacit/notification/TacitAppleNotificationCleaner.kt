package org.fuchss.matrix.tacit.notification

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import de.connect2x.sysnotify.NotificationHandler
import de.connect2x.sysnotify.NotificationPlatform
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.Worker
import de.connect2x.trixnity.messenger.notification.NotificationHandlers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val log = Logger("org.fuchss.matrix.tacit.notification.TacitAppleNotificationCleaner")

/**
 * Emits once every time the total notification count settles at 0, including once for an initial 0.
 *
 * Repeated zeros are collapsed before the [debounce], so a quick 0 -> 1 -> 0 (message read right after it arrived,
 * the case where macOS has not registered the notification yet) still fires, while a burst of read receipts only
 * fires once after [debounce] of quiet.
 */
@OptIn(FlowPreview::class)
internal fun Flow<Int>.allReadSignals(debounce: Duration): Flow<Unit> =
    map { it <= 0 }.distinctUntilChanged().debounce(debounce).filter { it }.map { }

/** Only the Apple handler needs this: sysnotify's `pop` misses notifications macOS has not registered yet. */
internal fun NotificationHandler.needsLeftoverCleanup(): Boolean = platform == NotificationPlatform.APPLE

/**
 * Works around sysnotify's async `push` on macOS: if a message is read before its notification is registered with
 * Notification Center, upstream's `pop` misses it and the notification plus the Dock badge stay forever. Once the
 * combined count over all accounts is 0, `clearAll()` removes whatever is left. Other platforms are untouched.
 */
class TacitAppleNotificationCleaner(
    private val matrixClients: MatrixClients,
    private val notificationHandlers: NotificationHandlers,
) : Worker {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork() {
        matrixClients
            .map { it.values }
            .flatMapLatest { clients -> totalCount(clients) }
            .allReadSignals(1.seconds)
            .collect { clearLeftovers() }
    }

    private fun totalCount(clients: Collection<MatrixClient>): Flow<Int> =
        if (clients.isEmpty()) flowOf(0)
        else combine(clients.map { it.notification.getCount() }) { it.sum() }

    private suspend fun clearLeftovers() =
        clearLeftoverNotifications(matrixClients.value.keys) { notificationHandlers[it] }
}

/** Clears the leftovers of every account whose handler needs it; one failing account never stops the others. */
internal suspend fun <A> clearLeftoverNotifications(accounts: Collection<A>, handlerOf: (A) -> NotificationHandler) {
    for (account in accounts) {
        try {
            val handler = handlerOf(account)
            if (handler.needsLeftoverCleanup()) handler.clearAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "failed to clear leftover notifications of $account" }
        }
    }
}
