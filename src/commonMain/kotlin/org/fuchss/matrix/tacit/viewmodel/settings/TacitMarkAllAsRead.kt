package org.fuchss.matrix.tacit.viewmodel.settings

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.MarkedUnreadEventContent
import de.connect2x.trixnity.core.model.events.m.ReceiptType
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val log = Logger("org.fuchss.matrix.tacit.viewmodel.settings.TacitMarkAllAsRead")

internal sealed interface TacitMarkAllAsReadState {
    data object Idle : TacitMarkAllAsReadState

    /** [total] is `null` while the rooms are still being checked. */
    data class Running(val processed: Int, val total: Int?) : TacitMarkAllAsReadState

    data class Done(val markedRooms: Int, val failedRooms: Int) : TacitMarkAllAsReadState

    data object Failed : TacitMarkAllAsReadState
}

/**
 * Marks every joined room of every logged-in account as read.
 *
 * trixnity only drops a room's stored notifications (which make up the global/tray count) once a sync delivers the
 * own receipt on the room's `lastEventId`. So the receipt is (re)sent for every room whose receipt is not on that
 * event yet, and also for rooms that still have notifications or are considered unread although it already is, to
 * force that re-evaluation. A set `m.marked_unread` flag is cleared as well.
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
        val roomId: RoomId,
        val receiptEventId: EventId?,
        val isPublic: Boolean,
        val clearMarkedUnread: Boolean,
    )

    private suspend fun markAll(): TacitMarkAllAsReadState.Done {
        val work = matrixClients.value.values.flatMap { client -> collectWork(client) }
        log.info { "Marking ${work.size} rooms as read" }
        _state.value = TacitMarkAllAsReadState.Running(processed = 0, total = work.size)
        var failed = 0
        work.forEachIndexed { index, item ->
            if (!apply(item)) failed++
            _state.value = TacitMarkAllAsReadState.Running(processed = index + 1, total = work.size)
        }
        return TacitMarkAllAsReadState.Done(markedRooms = work.size - failed, failedRooms = failed)
    }

    private suspend fun collectWork(client: MatrixClient): List<Work> {
        val userId = client.userId
        val isPublic = settings.get(userId).first()?.base?.readMarkerIsPublic ?: false
        val rooms = client.room.getAll().flattenValues().first()
        // Spaces are included on purpose: they never show up in the room list, but their notifications still count.
        return rooms.filter { it.membership == Membership.JOIN }.mapNotNull { room ->
            val roomId = room.roomId
            val markedUnread =
                client.room.getAccountData(roomId, MarkedUnreadEventContent::class).first()?.unread == true
            val lastEventId = room.lastEventId
            val needsReceipt = lastEventId != null && run {
                val ownReceipts = client.user.getReceiptsById(roomId, userId).first()?.receipts.orEmpty()
                val receiptOnLastEvent = ownReceipts[ReceiptType.Read]?.eventId == lastEventId ||
                        ownReceipts[ReceiptType.PrivateRead]?.eventId == lastEventId
                val notificationCount = client.notification.getCount(roomId).first()
                // With the marked-unread flag set, isUnread is true regardless of the receipt.
                val stateUnread = !markedUnread && client.notification.isUnread(roomId).first()
                if (notificationCount > 0 || stateUnread) {
                    log.info {
                        "Room $roomId ($userId): $notificationCount notifications, unread=$stateUnread, " +
                                "receiptOnLastEvent=$receiptOnLastEvent, " +
                                "space=${room.createEventContent?.type == RoomType.Space}"
                    }
                }
                !receiptOnLastEvent || notificationCount > 0 || stateUnread
            }
            if (!needsReceipt && !markedUnread) null
            else Work(
                client = client,
                roomId = roomId,
                receiptEventId = lastEventId.takeIf { needsReceipt },
                isPublic = isPublic,
                clearMarkedUnread = markedUnread,
            )
        }
    }

    private suspend fun apply(work: Work): Boolean {
        val client = work.client
        var success = true
        if (work.clearMarkedUnread) {
            client.api.room.setAccountData(MarkedUnreadEventContent(false), work.roomId, client.userId)
                .onFailure {
                    log.warn(it) { "Could not clear marked unread in ${work.roomId}" }
                    success = false
                }
        }
        val eventId = work.receiptEventId
        if (eventId != null) {
            client.api.room
                .setReadMarkers(
                    roomId = work.roomId,
                    fullyRead = eventId,
                    read = eventId.takeIf { work.isPublic },
                    privateRead = eventId,
                )
                .onFailure {
                    log.warn(it) { "Could not set read markers in ${work.roomId}" }
                    success = false
                }
        }
        return success
    }
}
