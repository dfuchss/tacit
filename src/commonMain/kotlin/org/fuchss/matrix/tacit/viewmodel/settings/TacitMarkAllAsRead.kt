package org.fuchss.matrix.tacit.viewmodel.settings

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.info
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.NotificationStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.StoredNotification
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.MarkedUnreadEventContent
import de.connect2x.trixnity.core.model.events.m.ReceiptType
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The stored notifications of [this] client. Their number is exactly what trixnity's `notification.getCount()`
 * (and so the tray/Dock badge) counts: it ignores dismissal, membership and whether the room still exists.
 */
internal fun MatrixClient.storedNotifications(): Flow<List<StoredNotification>> =
    di.get<NotificationStore>().getAll().flattenValues()

private val log = Logger("org.fuchss.matrix.tacit.viewmodel.settings.TacitMarkAllAsRead")

internal sealed interface TacitMarkAllAsReadState {
    data object Idle : TacitMarkAllAsReadState

    /** [total] is `null` while the rooms are still being checked. */
    data class Running(val processed: Int, val total: Int?) : TacitMarkAllAsReadState

    /** [pendingInvites] counts invites whose notifications keep the badge; they can only be accepted or declined. */
    data class Done(val markedRooms: Int, val failedRooms: Int, val pendingInvites: Int = 0) : TacitMarkAllAsReadState

    data object Failed : TacitMarkAllAsReadState
}

/**
 * Marks every joined room of every logged-in account as read (see [planMarkAllAsRead]), drops the stored
 * notifications of left or forgotten rooms and reports pending invites, whose notifications only go away when the
 * invite is accepted or declined.
 *
 * Lives in the messenger scope, so the run continues (and its result stays visible) when the settings are left.
 */
internal class TacitMarkAllAsRead(
    private val matrixClients: MatrixClients,
    private val settings: MatrixMessengerSettingsHolder,
    private val coroutineScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TacitMarkAllAsReadState>(TacitMarkAllAsReadState.Idle)
    val state: StateFlow<TacitMarkAllAsReadState> = _state.asStateFlow()

    fun start() {
        val previous = _state.value
        if (previous is TacitMarkAllAsReadState.Running) return
        if (!_state.compareAndSet(previous, TacitMarkAllAsReadState.Running(processed = 0, total = null))) return
        coroutineScope.launch {
            _state.value = try {
                markAll()
            } catch (e: CancellationException) {
                _state.value = TacitMarkAllAsReadState.Idle
                throw e
            } catch (e: Exception) {
                log.error(e) { "Marking all rooms as read failed" }
                TacitMarkAllAsReadState.Failed
            }
        }
    }

    private data class Work(
        val client: MatrixClient,
        val item: TacitMarkAllAsReadWork,
        val isPublic: Boolean,
    )

    private suspend fun markAll(): TacitMarkAllAsReadState.Done {
        val plans = matrixClients.value.values.map { client -> client to plan(client) }
        val work = plans.flatMap { (client, plan) ->
            val isPublic = settings.get(client.userId).first()?.base?.readMarkerIsPublic ?: false
            plan.work.map { Work(client, it, isPublic) }
        }
        val orphans = plans.flatMap { (client, plan) -> plan.orphans.map { client to it } }
        val pendingInvites = plans.sumOf { it.second.pendingInvites.size }
        plans.forEach { (client, plan) ->
            plan.pendingInvites.forEach { roomId ->
                log.info { "Room $roomId (${client.userId}): pending invite holds notifications; left as is" }
            }
        }
        log.info {
            "Marking ${work.size} rooms as read, clearing ${orphans.size} orphaned notification rooms, " +
                    "$pendingInvites pending invites"
        }
        val total = work.size + orphans.size
        _state.value = TacitMarkAllAsReadState.Running(processed = 0, total = total)
        var processed = 0
        var failed = 0
        work.forEach { item ->
            if (!apply(item)) failed++
            _state.value = TacitMarkAllAsReadState.Running(processed = ++processed, total = total)
        }
        orphans.forEach { (client, roomId) ->
            if (!clearOrphan(client, roomId)) failed++
            _state.value = TacitMarkAllAsReadState.Running(processed = ++processed, total = total)
        }
        return TacitMarkAllAsReadState.Done(
            markedRooms = total - failed,
            failedRooms = failed,
            pendingInvites = pendingInvites,
        )
    }

    internal suspend fun plan(client: MatrixClient): TacitMarkAllAsReadPlan {
        val userId = client.userId
        val notificationCounts = client.storedNotifications().first().groupingBy { it.roomId }.eachCount()
        val rooms = client.room.getAll().flattenValues().first()
        val snapshots = rooms.map { room ->
            val roomId = room.roomId
            val notificationCount = notificationCounts[roomId] ?: 0
            if (room.membership != Membership.JOIN) {
                return@map TacitRoomReadSnapshot(roomId, room.membership, notificationCount = notificationCount)
            }
            val markedUnread =
                client.room.getAccountData(roomId, MarkedUnreadEventContent::class).first()?.unread == true
            val lastEventId = room.lastEventId
            val ownReceipts =
                if (lastEventId == null) emptyMap()
                else client.user.getReceiptsById(roomId, userId).first()?.receipts.orEmpty()
            val receiptOnLastEvent = lastEventId != null &&
                    (ownReceipts[ReceiptType.Read]?.eventId == lastEventId ||
                            ownReceipts[ReceiptType.PrivateRead]?.eventId == lastEventId)
            // With the marked-unread flag set, isUnread is true regardless of the receipt.
            val stateUnread = lastEventId != null && !markedUnread && client.notification.isUnread(roomId).first()
            if (notificationCount > 0 || stateUnread) {
                log.info {
                    "Room $roomId ($userId): $notificationCount notifications, unread=$stateUnread, " +
                            "receiptOnLastEvent=$receiptOnLastEvent, " +
                            "space=${room.createEventContent?.type == RoomType.Space}"
                }
            }
            TacitRoomReadSnapshot(
                roomId = roomId,
                membership = Membership.JOIN,
                lastEventId = lastEventId,
                ownReceiptOnLastEvent = receiptOnLastEvent,
                notificationCount = notificationCount,
                stateUnread = stateUnread,
                markedUnread = markedUnread,
            )
        }
        val known = rooms.map { it.roomId }.toSet()
        val missing = notificationCounts.filterKeys { it !in known }.map { (roomId, count) ->
            TacitRoomReadSnapshot(roomId, membership = null, notificationCount = count)
        }
        return planMarkAllAsRead(snapshots + missing)
    }

    /**
     * Drops the stored notifications of a left or forgotten room. This only touches the local notification store
     * (no server request and, unlike `forgetRoom`, no timeline/state of a left room that is kept on purpose).
     */
    private suspend fun clearOrphan(client: MatrixClient, roomId: RoomId): Boolean {
        val membership = client.room.getById(roomId).first()?.membership?.toString() ?: "not in store"
        return try {
            val notificationStore = client.di.get<NotificationStore>()
            client.di.get<StoreTransactionManager>().writeTransaction { notificationStore.deleteByRoomId(roomId) }
            log.info { "Room $roomId (${client.userId}): orphaned notifications cleared (membership=$membership)" }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "Room $roomId (${client.userId}): could not clear orphaned notifications" }
            false
        }
    }

    private suspend fun apply(work: Work): Boolean {
        val client = work.client
        var success = true
        val roomId = work.item.roomId
        if (work.item.clearMarkedUnread) {
            client.api.room.setAccountData(MarkedUnreadEventContent(false), roomId, client.userId)
                .onFailure {
                    log.warn(it) { "Could not clear marked unread in ${roomId}" }
                    success = false
                }
        }
        val eventId = work.item.receiptEventId
        if (eventId != null) {
            client.api.room
                .setReadMarkers(
                    roomId = roomId,
                    fullyRead = eventId,
                    read = eventId.takeIf { work.isPublic },
                    privateRead = eventId,
                )
                .onFailure {
                    log.warn(it) { "Could not set read markers in ${roomId}" }
                    success = false
                }
        }
        return success
    }
}
