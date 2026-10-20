package org.fuchss.matrix.tacit.views.room.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineFocusStateTest {
    @Test
    fun `skipping moves towards older elements by default`() {
        assertEquals(TimelineFocusSkip(4, towardsNewer = false), skipTimelineFocus(3, towardsNewer = false))
    }

    @Test
    fun `skipping keeps moving towards newer elements`() {
        assertEquals(TimelineFocusSkip(2, towardsNewer = true), skipTimelineFocus(3, towardsNewer = true))
    }

    @Test
    fun `skipping turns around at the newest element`() {
        assertEquals(TimelineFocusSkip(1, towardsNewer = false), skipTimelineFocus(0, towardsNewer = true))
    }

    @Test
    fun `empty elements and collapsed members other than the head render nothing`() {
        assertTrue(rendersNothingInTimeline(isEmpty = true, isGroupedSystemEvent = false, groupExpanded = false, isGroupHead = false))
        assertTrue(rendersNothingInTimeline(isEmpty = false, isGroupedSystemEvent = true, groupExpanded = false, isGroupHead = false))
        assertFalse(rendersNothingInTimeline(isEmpty = false, isGroupedSystemEvent = true, groupExpanded = false, isGroupHead = true))
        assertFalse(rendersNothingInTimeline(isEmpty = false, isGroupedSystemEvent = true, groupExpanded = true, isGroupHead = false))
        assertFalse(rendersNothingInTimeline(isEmpty = false, isGroupedSystemEvent = false, groupExpanded = false, isGroupHead = false))
    }

    @Test
    fun `focus passes over an element that renders nothing in the direction it travels`() {
        val state = TimelineFocusState(TimelineFocusTravel())
        state.onFocus(5)
        // Moving up (older): 6 renders nothing and passes the focus on to 7.
        state.onFocus(6)
        state.skipFrom(6)
        assertEquals(7, state.focusedIndex)
        // Moving down (newer) from 7: 6 renders nothing and passes the focus on to 5.
        state.onFocus(6)
        state.skipFrom(6)
        assertEquals(5, state.focusedIndex)
    }

    @Test
    fun `skipping from an element that lost the focus does nothing`() {
        val state = TimelineFocusState(TimelineFocusTravel())
        state.onFocus(2)
        state.skipFrom(1)
        assertEquals(2, state.focusedIndex)
    }

    @Test
    fun `a reset focus starts at the newest element and keeps the travel direction`() {
        val travel = TimelineFocusTravel()
        TimelineFocusState(travel).apply {
            onFocus(4)
            onFocus(2)
        }
        assertTrue(travel.towardsNewer)
        val reset = TimelineFocusState(travel)
        assertEquals(0, reset.focusedIndex)
        reset.skipFrom(1)
        assertEquals(0, reset.focusedIndex)
    }
}
