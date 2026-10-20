package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import de.connect2x.trixnity.messenger.compose.view.room.timeline.TimelineViewElement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** How the list moves for a jump (see [planTimelineJump]). */
internal sealed interface TimelineJumpScroll {
    /** The target is the newest element: pin the list to its very end. */
    data object ToEnd : TimelineJumpScroll

    /** Reveal the element at [elementIndex] (into the rendered elements) without jumping to the end. */
    data class Reveal(val elementIndex: Int) : TimelineJumpScroll

    /** The target was not found. */
    data object None : TimelineJumpScroll
}

internal data class TimelineJumpPlan(val unfoldGroup: Boolean, val scroll: TimelineJumpScroll, val highlight: Boolean)

/**
 * What a jump to the rendered element at [index] (-1: not found) does.
 *
 * - A jump to an event inside a collapsed system event group ([targetInCollapsedGroup]) unfolds it, so
 *   the target is shown instead of a zero-height placeholder. Only the plain jump to the end (index 0,
 *   not following a reply press) leaves the groups alone.
 * - Index 0 is the newest element, i.e. the request is "go to the end of the timeline" (that is what
 *   the jump-to-end button asks for); any other element is merely revealed.
 * - The target lights up when the jump follows a press on a reply quote ([followsReplyPress]), but not
 *   when it is the newest element.
 */
internal fun planTimelineJump(index: Int, followsReplyPress: Boolean, targetInCollapsedGroup: Boolean): TimelineJumpPlan =
    TimelineJumpPlan(
        unfoldGroup = targetInCollapsedGroup && (index > 0 || (index == 0 && followsReplyPress)),
        scroll = when {
            index == 0 -> TimelineJumpScroll.ToEnd
            index > 0 -> TimelineJumpScroll.Reveal(index)
            else -> TimelineJumpScroll.None
        },
        highlight = index > 0 && followsReplyPress,
    )

/** The lazy list index of the rendered element at [elementIndex]: the typing indicator is an extra item at the end. */
internal fun timelineLazyIndex(elementIndex: Int, typingIndicatorShown: Boolean): Int =
    if (typingIndicatorShown) elementIndex + 1 else elementIndex

/**
 * Carries out the view model's scroll requests ([requests]): finds the target among the [rendered]
 * elements, unfolds its collapsed group ([expansion]), scrolls ([scrollState]), lights the target up
 * after a press on a reply quote ([highlight]) and acknowledges the request.
 */
@Composable
internal fun TimelineJumpEffect(
    requests: TimelineScrollRequests,
    scrollState: TimelineScrollState,
    rendered: State<List<TimelineViewElement>>,
    groups: State<TacitSystemEventGroups>,
    expansion: SystemEventExpansionState,
    highlight: JumpHighlightState,
    showTypingIndicator: State<Boolean>,
) {
    val target = requests.pending
    LaunchedEffect(target, showTypingIndicator.value) {
        val key = target ?: return@LaunchedEffect
        // The requested element is not necessarily rendered yet; the view model may still be
        // (re-)loading that part of the timeline. `indexOfFirst` does not suspend, so actually wait
        // for the element to show up instead of giving up after the first look. The budget stays
        // below the view model's own five second deadline so that the acknowledgement still reaches it.
        val index = withTimeoutOrNull(4.seconds) {
            snapshotFlow { rendered.value.indexOfFirst { it.key == key } }.first { it >= 0 }
        } ?: -1
        val followsReplyPress = highlight.replyJumpSignal.consumeRecentPress()
        val group = groups.value[key]
        val plan = planTimelineJump(index, followsReplyPress, targetInCollapsedGroup = group != null && !expansion.isExpanded(group))
        if (plan.unfoldGroup && group != null) {
            // Snap the group open (no expand animation) and let the list compose and measure it
            // before scrolling: scrolling during the animation would use the offsets of a half-open
            // group and miss the target.
            expansion.snapOpen(key, group)
            withFrameNanos { }
            withFrameNanos { }
        }
        when (val scroll = plan.scroll) {
            TimelineJumpScroll.ToEnd -> scrollState.scrollToEnd()
            is TimelineJumpScroll.Reveal -> scrollState.reveal(timelineLazyIndex(scroll.elementIndex, showTypingIndicator.value))
            TimelineJumpScroll.None -> Unit
        }
        expansion.endSnapOpen()
        if (plan.highlight) highlight.highlight(key)
        // Acknowledge even when the element could not be found: there is nothing left to wait for.
        requests.complete(key)
    }
}
