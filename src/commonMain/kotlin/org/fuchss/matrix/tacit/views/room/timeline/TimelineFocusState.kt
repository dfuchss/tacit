package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Where the roving focus goes from an element that renders nothing, and the direction it travels in then. */
internal data class TimelineFocusSkip(val index: Int, val towardsNewer: Boolean)

/**
 * Where the roving focus goes from the element at [index], which renders nothing: the nearest element
 * of the [count] rendered ones that renders something ([rendersNothing] is `false`), searched in the
 * direction the focus last travelled, so it can move past a collapsed group towards newer elements
 * as well. The list is reversed: index 0 is the newest element, so "newer" is the lower index. With
 * nothing left in that direction the search turns around (at the newest or the oldest element).
 * `null` if no element renders anything.
 *
 * A whole run of such elements (a collapsed group of 30) is resolved at once, so the focus lands on
 * its final element in one step instead of travelling one element (and one frame) at a time.
 */
internal fun skipTimelineFocus(
    index: Int,
    towardsNewer: Boolean,
    count: Int,
    rendersNothing: (Int) -> Boolean,
): TimelineFocusSkip? {
    fun newer() = (index - 1 downTo 0).firstOrNull { !rendersNothing(it) }?.let { TimelineFocusSkip(it, towardsNewer = true) }
    fun older() = (index + 1 until count).firstOrNull { !rendersNothing(it) }?.let { TimelineFocusSkip(it, towardsNewer = false) }
    return if (towardsNewer) newer() ?: older() else older() ?: newer()
}

/**
 * Whether a timeline element renders nothing and therefore cannot hold the roving focus: an Empty
 * element (unsupported or ignored event) or a collapsed group member other than the group's head.
 */
internal fun rendersNothingInTimeline(
    isEmpty: Boolean,
    isGroupedSystemEvent: Boolean,
    groupExpanded: Boolean,
    isGroupHead: Boolean,
): Boolean = isEmpty || (isGroupedSystemEvent && !groupExpanded && !isGroupHead)

/** Direction the timeline's roving keyboard focus last moved in; outlives [TimelineFocusState] resets. */
internal class TimelineFocusTravel(var towardsNewer: Boolean = false)

/**
 * The timeline's roving keyboard focus: the index (into the rendered elements) of the element that
 * takes the focus when tabbing into the list. The typing indicator is no rendered element (its item
 * is not counted by `itemsIndexed`) and never takes the focus, so index 0 is always the newest
 * element -- like upstream, which ignores the "typing" key. Elements that render nothing hand it on via [skipFrom],
 * from an effect -- never while composing.
 */
@Stable
internal class TimelineFocusState(private val travel: TimelineFocusTravel) {
    var focusedIndex by mutableIntStateOf(0)
        private set

    fun isFocused(index: Int): Boolean = focusedIndex == index

    fun onFocus(index: Int) {
        travel.towardsNewer = index < focusedIndex
        focusedIndex = index
    }

    /**
     * Moves the focus off the element at [index], which renders nothing, if it still has it: straight
     * to the nearest of the [count] elements that renders something (see [skipTimelineFocus]).
     */
    fun skipFrom(index: Int, count: Int, rendersNothing: (Int) -> Boolean) {
        if (focusedIndex != index) return
        val skip = skipTimelineFocus(index, travel.towardsNewer, count, rendersNothing) ?: return
        travel.towardsNewer = skip.towardsNewer
        focusedIndex = skip.index
    }
}

/** The focus resets to the newest element whenever [resetKeys] change; the travel direction is kept. */
@Composable
internal fun rememberTimelineFocusState(vararg resetKeys: Any?): TimelineFocusState {
    val travel = remember { TimelineFocusTravel() }
    return remember(*resetKeys) { TimelineFocusState(travel) }
}
