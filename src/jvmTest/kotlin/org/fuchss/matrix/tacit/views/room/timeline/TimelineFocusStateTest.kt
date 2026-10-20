package org.fuchss.matrix.tacit.views.room.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineFocusStateTest {
    private fun hidden(vararg indices: Int): (Int) -> Boolean = { it in indices }

    private val nothingHidden = hidden()

    @Test
    fun `skipping moves towards older elements by default`() {
        assertEquals(TimelineFocusSkip(4, towardsNewer = false), skipTimelineFocus(3, towardsNewer = false, count = 10, nothingHidden))
    }

    @Test
    fun `skipping keeps moving towards newer elements`() {
        assertEquals(TimelineFocusSkip(2, towardsNewer = true), skipTimelineFocus(3, towardsNewer = true, count = 10, nothingHidden))
    }

    @Test
    fun `skipping turns around at the newest element`() {
        assertEquals(TimelineFocusSkip(1, towardsNewer = false), skipTimelineFocus(0, towardsNewer = true, count = 10, nothingHidden))
    }

    @Test
    fun `skipping passes a whole run of elements that render nothing at once`() {
        // A collapsed group of 30: its head at 31 renders, members 1..30 do not.
        val collapsed = { i: Int -> i in 1..30 }
        assertEquals(TimelineFocusSkip(31, towardsNewer = false), skipTimelineFocus(1, towardsNewer = false, count = 40, collapsed))
        assertEquals(TimelineFocusSkip(0, towardsNewer = true), skipTimelineFocus(30, towardsNewer = true, count = 40, collapsed))
    }

    @Test
    fun `skipping turns around at the newest element past a run`() {
        assertEquals(TimelineFocusSkip(4, towardsNewer = false), skipTimelineFocus(2, towardsNewer = true, count = 10, hidden(0, 1, 2, 3)))
    }

    @Test
    fun `skipping turns around at the oldest element`() {
        assertEquals(TimelineFocusSkip(5, towardsNewer = true), skipTimelineFocus(7, towardsNewer = false, count = 10, hidden(6, 7, 8, 9)))
    }

    @Test
    fun `skipping finds nothing when no element renders anything`() {
        assertNull(skipTimelineFocus(1, towardsNewer = false, count = 3) { true })
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
        // Moving up (older): 6..8 render nothing and pass the focus on to 9 at once.
        state.onFocus(6)
        state.skipFrom(6, count = 20, hidden(6, 7, 8))
        assertEquals(9, state.focusedIndex)
        // Moving down (newer) from 9: 8..6 render nothing and pass the focus on to 5.
        state.onFocus(8)
        state.skipFrom(8, count = 20, hidden(6, 7, 8))
        assertEquals(5, state.focusedIndex)
    }

    @Test
    fun `focus stays put when no element renders anything`() {
        val state = TimelineFocusState(TimelineFocusTravel())
        state.onFocus(1)
        state.skipFrom(1, count = 3) { true }
        assertEquals(1, state.focusedIndex)
    }

    @Test
    fun `skipping from an element that lost the focus does nothing`() {
        val state = TimelineFocusState(TimelineFocusTravel())
        state.onFocus(2)
        state.skipFrom(1, count = 10, hidden(1))
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
        reset.skipFrom(1, count = 10, hidden(1))
        assertEquals(0, reset.focusedIndex)
    }
}
