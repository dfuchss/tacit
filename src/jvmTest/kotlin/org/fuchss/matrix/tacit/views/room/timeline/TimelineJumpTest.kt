package org.fuchss.matrix.tacit.views.room.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineJumpTest {
    @Test
    fun `jump to an older element reveals it`() {
        assertEquals(
            TimelineJumpPlan(unfoldGroup = false, scroll = TimelineJumpScroll.Reveal(7), highlight = false),
            planTimelineJump(7, followsReplyPress = false, targetInCollapsedGroup = false),
        )
    }

    @Test
    fun `jump from a reply quote highlights the target`() {
        assertEquals(
            TimelineJumpPlan(unfoldGroup = false, scroll = TimelineJumpScroll.Reveal(7), highlight = true),
            planTimelineJump(7, followsReplyPress = true, targetInCollapsedGroup = false),
        )
    }

    @Test
    fun `jump into a collapsed group unfolds it`() {
        assertTrue(planTimelineJump(3, followsReplyPress = false, targetInCollapsedGroup = true).unfoldGroup)
        assertTrue(planTimelineJump(3, followsReplyPress = true, targetInCollapsedGroup = true).unfoldGroup)
        assertFalse(planTimelineJump(3, followsReplyPress = true, targetInCollapsedGroup = false).unfoldGroup)
    }

    @Test
    fun `plain jump to the end pins the list and leaves the groups alone`() {
        assertEquals(
            TimelineJumpPlan(unfoldGroup = false, scroll = TimelineJumpScroll.ToEnd, highlight = false),
            planTimelineJump(0, followsReplyPress = false, targetInCollapsedGroup = true),
        )
    }

    @Test
    fun `reply jump to the newest element unfolds its group but does not highlight`() {
        assertEquals(
            TimelineJumpPlan(unfoldGroup = true, scroll = TimelineJumpScroll.ToEnd, highlight = false),
            planTimelineJump(0, followsReplyPress = true, targetInCollapsedGroup = true),
        )
    }

    @Test
    fun `missing target neither scrolls nor unfolds nor highlights`() {
        assertEquals(
            TimelineJumpPlan(unfoldGroup = false, scroll = TimelineJumpScroll.None, highlight = false),
            planTimelineJump(-1, followsReplyPress = true, targetInCollapsedGroup = true),
        )
    }

    @Test
    fun `typing indicator shifts the lazy index by one`() {
        assertEquals(5, timelineLazyIndex(5, typingIndicatorShown = false))
        assertEquals(6, timelineLazyIndex(5, typingIndicatorShown = true))
    }
}
