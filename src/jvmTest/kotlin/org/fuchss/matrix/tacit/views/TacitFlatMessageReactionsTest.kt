package org.fuchss.matrix.tacit.views

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.viewmodel.UserInfoElement
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.EventIdOrTransactionId.Companion.EventIdOrTransactionId
import de.connect2x.trixnity.messenger.viewmodel.util.EventReaction
import de.connect2x.trixnity.messenger.viewmodel.util.EventReactions
import de.connect2x.trixnity.messenger.viewmodel.util.ReactionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TacitFlatMessageReactionsTest {
    private fun reaction(key: String, user: String, status: ReactionStatus, shortcode: String? = null) =
        EventReaction(
            value = key,
            sender = UserInfoElement(UserId(user, "server"), user, ""),
            eventOrTransactionId = EventIdOrTransactionId(EventId("\$$user-$key")),
            status = status,
            shortcode = shortcode,
        )

    private fun info(vararg reactions: EventReaction): EventReactions.ByReactionsInfo =
        EventReactions(reactions.toSet()).byReaction.values.single()

    @Test
    fun ownSentPendingOrFailedReactionsCountAsMine() {
        assertTrue(info(reaction("👍", "me", ReactionStatus.Sent)).reactedByMe())
        assertTrue(info(reaction("👍", "me", ReactionStatus.Pending)).reactedByMe())
        assertTrue(info(reaction("👍", "me", ReactionStatus.SentError("boom"))).reactedByMe())
        assertTrue(
            info(reaction("👍", "other", ReactionStatus.FromOtherAccount), reaction("👍", "me", ReactionStatus.Sent))
                .reactedByMe()
        )
    }

    @Test
    fun othersAndPendingRedactionsDoNotCountAsMine() {
        assertFalse(info(reaction("👍", "other", ReactionStatus.FromOtherAccount)).reactedByMe())
        // A second click while the redaction is on its way must react again, not redact twice.
        assertFalse(info(reaction("👍", "me", ReactionStatus.RedactionPending)).reactedByMe())
    }

    @Test
    fun imageReactionsShowTheirShortcode() {
        val image = info(reaction("mxc://server/abc", "other", ReactionStatus.FromOtherAccount, shortcode = ":party:"))
        assertEquals(":party:", image.label("mxc://server/abc"))
        val withoutShortcode = info(reaction("mxc://server/abc", "other", ReactionStatus.FromOtherAccount))
        assertEquals("mxc://server/abc", withoutShortcode.label("mxc://server/abc"))
        val emoji = info(reaction("👍", "other", ReactionStatus.FromOtherAccount, shortcode = ":thumbsup:"))
        assertEquals("👍", emoji.label("👍"))
    }
}
