package org.fuchss.matrix.tacit.viewmodel.settings

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.Membership

/**
 * Everything [planMarkAllAsRead] needs to know about one room of one account.
 *
 * Rooms that only appear in the notification store (their room was already forgotten) are passed with
 * [membership] `null`.
 */
internal data class TacitRoomReadSnapshot(
    val roomId: RoomId,
    /** `null` when the room is not in the room store. */
    val membership: Membership?,
    val lastEventId: EventId? = null,
    /** The own `m.read` or `m.read.private` receipt is on [lastEventId]. */
    val ownReceiptOnLastEvent: Boolean = false,
    /** Stored notifications of this room; they make up the tray/Dock count. */
    val notificationCount: Int = 0,
    /** trixnity considers the room unread (ignoring [markedUnread]). */
    val stateUnread: Boolean = false,
    val markedUnread: Boolean = false,
) {
    val existsInStore: Boolean get() = membership != null
}

internal data class TacitMarkAllAsReadWork(
    val roomId: RoomId,
    /** The event to put the read markers on, or `null` when only [clearMarkedUnread] is needed. */
    val receiptEventId: EventId?,
    val clearMarkedUnread: Boolean,
)

internal data class TacitMarkAllAsReadPlan(
    val work: List<TacitMarkAllAsReadWork>,
    /** Invites that hold notifications. Reading cannot clear them; only accepting or declining does. */
    val pendingInvites: List<RoomId>,
    /** Rooms with stored notifications that are gone from the store or were left (LEAVE/BAN). */
    val orphans: List<RoomId>,
)

/**
 * Decides per room what "mark all as read" has to do.
 *
 * trixnity only drops a joined room's stored notifications once a sync delivers the own receipt on the room's
 * `lastEventId`. So the receipt is (re)sent for every joined room whose receipt is not on that event yet, and also
 * for rooms that still have notifications or are considered unread although it already is, to force that
 * re-evaluation. A set `m.marked_unread` flag is cleared as well. Spaces are included on purpose: they never show up
 * in the room list, but their notifications still count.
 *
 * Notifications of invites and of left or forgotten rooms are not affected by receipts; they are reported instead.
 */
internal fun planMarkAllAsRead(rooms: List<TacitRoomReadSnapshot>): TacitMarkAllAsReadPlan {
    val work = mutableListOf<TacitMarkAllAsReadWork>()
    val pendingInvites = mutableListOf<RoomId>()
    val orphans = mutableListOf<RoomId>()
    for (room in rooms) {
        when (room.membership) {
            Membership.JOIN -> {
                val needsReceipt = room.lastEventId != null &&
                        (!room.ownReceiptOnLastEvent || room.notificationCount > 0 || room.stateUnread)
                if (needsReceipt || room.markedUnread) {
                    work += TacitMarkAllAsReadWork(
                        roomId = room.roomId,
                        receiptEventId = room.lastEventId.takeIf { needsReceipt },
                        clearMarkedUnread = room.markedUnread,
                    )
                }
            }

            Membership.INVITE -> if (room.notificationCount > 0) pendingInvites += room.roomId
            null, Membership.LEAVE, Membership.BAN -> if (room.notificationCount > 0) orphans += room.roomId
            else -> Unit // KNOCK: nothing to read, nothing to clean up
        }
    }
    return TacitMarkAllAsReadPlan(work = work, pendingInvites = pendingInvites, orphans = orphans)
}
