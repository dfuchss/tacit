package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import de.connect2x.trixnity.messenger.compose.view.util.scrollIntoView
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitTimelineViewModel

/** Whether the list shows its very end: the newest item ([newestVisibleIndex] 0) sits flush at the bottom. */
internal fun isPinnedToEnd(newestVisibleIndex: Int?, newestVisibleOffset: Int?): Boolean =
    newestVisibleIndex == 0 && newestVisibleOffset == 0

/**
 * Whether the list follows a new newest element to the end: only when the newest element actually
 * changed (not on the first look, [previousKey] `null`) and the list was pinned to the end before.
 */
internal fun shouldFollowNewest(previousKey: String?, newestKey: String?, wasPinnedToEnd: Boolean): Boolean =
    previousKey != null && newestKey != previousKey && wasPinnedToEnd

/**
 * Scroll requests of the view model ([TimelineViewModel.scrollTo]) that wait for the list, and their
 * acknowledgement. The view model waits for [finished] and warns ("could not scroll to ..., because UI
 * did not set finishedScrollTo") when it never arrives. Lives outside the list, which only exists once
 * the timeline has loaded.
 */
@Stable
internal class TimelineScrollRequests {
    /** The key of the element to scroll to next, if any. */
    var pending by mutableStateOf<String?>(null)
        private set

    /** Handed to upstream's view state reporting, which clears it once the view model has it. */
    val finished: MutableState<String?> = mutableStateOf(null)

    fun request(key: String?) {
        pending = key
    }

    /** Acknowledges the request for [key], whether or not the element was found. */
    fun complete(key: String) {
        finished.value = key
        pending = null
    }
}

@Composable
internal fun rememberTimelineScrollRequests(timelineViewModel: TimelineViewModel): TimelineScrollRequests {
    val requests = remember(timelineViewModel) { TimelineScrollRequests() }
    LaunchedEffect(requests, timelineViewModel) {
        // The first value is the initial position, see upstream's getInitialFirstVisibleItemIndex.
        timelineViewModel.scrollTo.drop(1).collect { requests.request(it) }
    }
    return requests
}

/**
 * Scroll position logic of the (reversed) timeline list: whether it is pinned to its end, the
 * initial anchor, and following new messages while pinned. Plain fields where only effects read them.
 */
@Stable
internal class TimelineScrollState(val listState: LazyListState) {
    val isPinnedToEnd: State<Boolean> = derivedStateOf {
        val newestVisibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        isPinnedToEnd(newestVisibleItem?.index, newestVisibleItem?.offset)
    }
    val canScrollToEnd: State<Boolean> = derivedStateOf { !isPinnedToEnd.value }

    private var initialAnchorApplied = false
    private var wasPinnedToEnd = false
    private var previousNewestKey: String? = null

    /** Scrolls to the initial position [index] once (the list may have been laid out without it). */
    suspend fun applyInitialAnchor(index: Int) {
        if (initialAnchorApplied) return
        if (index == 0) listState.scrollToItem(0) else listState.safeScrollIntoView(index)
        initialAnchorApplied = true
    }

    /**
     * Pins the list to its very end. Not `scrollIntoView`: that positions the item flush with the
     * viewport start *including* the content padding, so the list would stop a few pixels short,
     * [isPinnedToEnd] would never become true and the jump-to-end button would stay on screen.
     */
    suspend fun scrollToEnd() {
        listState.animateScrollToItem(0)
    }

    /** Reveals the lazy item at [index] without jumping to the end. */
    suspend fun reveal(index: Int) {
        listState.safeScrollIntoView(index)
    }

    /** Keeps [wasPinnedToEnd] up to date; runs for as long as the list is shown. */
    suspend fun trackPinnedToEnd() {
        snapshotFlow { isPinnedToEnd.value }.distinctUntilChanged().collect { wasPinnedToEnd = it }
    }

    /** Follows a new newest element to the end if the list was pinned there (see [shouldFollowNewest]). */
    suspend fun onNewestElement(newestKey: String?) {
        val previousKey = previousNewestKey
        previousNewestKey = newestKey
        if (shouldFollowNewest(previousKey, newestKey, wasPinnedToEnd)) scrollToEnd()
    }
}

/**
 * The scroll state of the timeline list, with the effects that drive it: the initial anchor at
 * [initialIndex] and the view model's scroll-to-end requests. See also [TimelineFollowEffects].
 */
@Composable
internal fun rememberTimelineScrollState(
    listState: LazyListState,
    tacitTimelineViewModel: TacitTimelineViewModel?,
    initialIndex: Int?,
    showTypingIndicator: State<Boolean>,
): TimelineScrollState {
    val state = remember(listState) { TimelineScrollState(listState) }
    LaunchedEffect(state, initialIndex, showTypingIndicator.value) {
        state.applyInitialAnchor(initialIndex ?: 0)
    }
    LaunchedEffect(state, tacitTimelineViewModel) {
        tacitTimelineViewModel?.scrollToEndRequests?.collect { state.scrollToEnd() }
    }
    return state
}

/**
 * The effects of [TimelineScrollState] that run after the jump handling (see [TimelineJumpEffect]),
 * kept apart so the effects keep their order: the "caught up" report (the newest content is shown,
 * nothing newer is left to load, and the window has the focus) and following new messages.
 */
@Composable
internal fun TimelineFollowEffects(
    state: TimelineScrollState,
    tacitTimelineViewModel: TacitTimelineViewModel?,
    showTypingIndicator: State<Boolean>,
    newestKey: String?,
) {
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(state, tacitTimelineViewModel, windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused && showTypingIndicator.value && state.isPinnedToEnd.value }
            .distinctUntilChanged()
            .collect { tacitTimelineViewModel?.setCaughtUp(it) }
    }
    LaunchedEffect(state) { state.trackPinnedToEnd() }
    LaunchedEffect(state, newestKey, showTypingIndicator.value) { state.onNewestElement(newestKey) }
}

/**
 * Upstream [scrollIntoView] picks its reference points with `first()`/`last()` on the items that lie
 * *completely* inside the viewport and therefore throws `NoSuchElementException: List is empty.` when
 * there is none -- e.g. when the anchor (the read marker of an unread room) is taller than the
 * timeline, or the list has not been laid out yet. Anchor the item at the viewport start instead,
 * which is what upstream does for an item at or before the first visible one.
 */
private suspend fun LazyListState.safeScrollIntoView(index: Int) {
    val layoutInfo = layoutInfo
    val hasFullyVisibleItem = layoutInfo.visibleItemsInfo.any { item ->
        item.offset >= layoutInfo.viewportStartOffset && item.offset + item.size <= layoutInfo.viewportEndOffset
    }
    if (hasFullyVisibleItem) {
        scrollIntoView(index)
    } else {
        scrollToItem(index, -layoutInfo.viewportStartOffset)
    }
}
