package org.fuchss.matrix.tacit.viewmodel.settings

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TacitMarkAllAsReadPlannerTest {
    private val last = EventId("\$last")

    private fun joined(id: String, receiptOnLast: Boolean, notifications: Int = 0, markedUnread: Boolean = false) =
        TacitRoomReadSnapshot(
            roomId = RoomId("!$id:example.org"),
            membership = Membership.JOIN,
            lastEventId = last,
            ownReceiptOnLastEvent = receiptOnLast,
            notificationCount = notifications,
            markedUnread = markedUnread,
        )

    @Test
    fun inviteIsReportedAndNotMarked() {
        val invite = TacitRoomReadSnapshot(RoomId("!invite:example.org"), Membership.INVITE, notificationCount = 1)
        val plan = planMarkAllAsRead(listOf(invite))
        assertEquals(listOf(invite.roomId), plan.pendingInvites)
        assertTrue(plan.work.isEmpty())
        assertTrue(plan.orphans.isEmpty())
    }

    @Test
    fun inviteWithoutNotificationsIsNotReported() {
        val invite = TacitRoomReadSnapshot(RoomId("!invite:example.org"), Membership.INVITE)
        assertEquals(TacitMarkAllAsReadPlan(emptyList(), emptyList(), emptyList()), planMarkAllAsRead(listOf(invite)))
    }

    @Test
    fun missingAndLeftRoomsWithNotificationsAreOrphans() {
        val missing = TacitRoomReadSnapshot(RoomId("!gone:example.org"), membership = null, notificationCount = 2)
        val left = TacitRoomReadSnapshot(RoomId("!left:example.org"), Membership.LEAVE, notificationCount = 1)
        val banned = TacitRoomReadSnapshot(RoomId("!ban:example.org"), Membership.BAN, notificationCount = 1)
        val leftClean = TacitRoomReadSnapshot(RoomId("!clean:example.org"), Membership.LEAVE)
        val plan = planMarkAllAsRead(listOf(missing, left, banned, leftClean))
        assertEquals(listOf(missing.roomId, left.roomId, banned.roomId), plan.orphans)
        assertTrue(plan.work.isEmpty())
        assertTrue(plan.pendingInvites.isEmpty())
    }

    @Test
    fun readJoinedRoomWithNotificationsIsStillSent() {
        val room = joined("stuck", receiptOnLast = true, notifications = 1)
        val plan = planMarkAllAsRead(listOf(room))
        assertEquals(listOf(TacitMarkAllAsReadWork(room.roomId, last, clearMarkedUnread = false)), plan.work)
    }

    @Test
    fun alreadyReadJoinedRoomIsSkipped() {
        assertTrue(planMarkAllAsRead(listOf(joined("read", receiptOnLast = true))).work.isEmpty())
    }

    @Test
    fun unreadJoinedRoomGetsReceipt() {
        val room = joined("unread", receiptOnLast = false)
        assertEquals(listOf(TacitMarkAllAsReadWork(room.roomId, last, false)), planMarkAllAsRead(listOf(room)).work)
    }

    @Test
    fun markedUnreadReadRoomOnlyClearsTheFlag() {
        val room = joined("flag", receiptOnLast = true, markedUnread = true)
        assertEquals(
            listOf(TacitMarkAllAsReadWork(room.roomId, receiptEventId = null, clearMarkedUnread = true)),
            planMarkAllAsRead(listOf(room)).work,
        )
    }
}
