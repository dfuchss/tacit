package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.VerticalScrollbar
import de.connect2x.trixnity.messenger.compose.view.common.LoadingSpinner
import de.connect2x.trixnity.messenger.compose.view.common.modifier.rovingFocusContainer
import de.connect2x.trixnity.messenger.compose.view.common.modifier.rovingFocusItem
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.timeline.*
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementHolder
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*
import de.connect2x.trixnity.messenger.compose.view.theme.messengerIcons
import de.connect2x.trixnity.messenger.compose.view.util.scrollIntoView
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.RedactedTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.util.throttleFirst
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitTimelineViewModel
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val additionalEndPadding = 8
private val timelineStartPadding = 10.dp
private val timelineEndPadding = (10 + additionalEndPadding).dp

/** Height of the soft fade below the floating date header's opaque backdrop. */
private val floatingDateHeaderFadeHeight = 16.dp

private fun buildRenderableTimelineElements(
    timelineViewElements: List<TimelineViewElement>,
): List<TimelineViewElement> {
    val visibleViewModels = timelineViewElements
        .filterIsInstance<TimelineViewElement.Element>()
        .map { it.viewModel }
        .filterNot { it.element.value is RedactedTimelineElementViewModel }
        .asReversed()

    return buildList(visibleViewModels.size * 2) {
        var lastDate: String? = null
        for (viewModel in visibleViewModels) {
            when {
                lastDate == viewModel.formattedDate -> add(TimelineViewElement.Element(viewModel))
                viewModel.element.value is TimelineElementViewModel.Empty -> add(TimelineViewElement.Element(viewModel))
                else -> {
                    add(TimelineViewElement.Date(viewModel))
                    add(TimelineViewElement.Element(viewModel))
                    lastDate = viewModel.formattedDate
                }
            }
        }
    }.asReversed()
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

/** Direction the timeline's roving keyboard focus last moved in (the list is reversed: index 0 is the newest). */
private class TimelineFocusTravel(var towardsNewer: Boolean = false)

class TacitTimelineView : TimelineView {
    @Composable
    override fun ColumnScope.create(timelineViewModel: TimelineViewModel) {
        key(timelineViewModel) {
            val i18n = DI.get<TacitI18nView>()
            var scrollTo by remember { mutableStateOf<String?>(null) }
            // A scroll that follows a press on a reply quote is a jump to the quoted message: that
            // row lights up briefly so the eye finds it (see LocalTacitJumpHighlightKey).
            val replyJumpSignal = remember { TacitReplyJumpSignal() }
            var jumpHighlightKey by remember { mutableStateOf<String?>(null) }
            var jumpHighlightToken by remember { mutableStateOf(0) }
            LaunchedEffect(jumpHighlightToken) {
                if (jumpHighlightKey != null) {
                    delay(1_600)
                    jumpHighlightKey = null
                }
            }
            LaunchedEffect(timelineViewModel) {
                timelineViewModel.scrollTo.drop(1).collect { scrollTo = it }
            }

            val timelineViewElements = rememberTimelineViewElements(timelineViewModel)
            val renderedTimelineViewElements = remember(timelineViewElements.value) {
                derivedStateOf {
                    buildRenderableTimelineElements(timelineViewElements.value)
                }
            }
            // Runs of consecutive system events collapse into one expandable row (see
            // TacitSystemEventGroups). Grouping happens per element, not by merging lazy items: every
            // element keeps its own item, key and index, so scroll anchoring, index-based scrolling,
            // visible-item reporting (read receipts, read marker) and the unread marker are untouched.
            val systemEventGroups = rememberTacitSystemEventGroups(timelineViewElements)
            // Expanded groups, remembered while their events stay loaded. Stored as the member keys of
            // the expanded groups; a group is expanded when any of its members is in there. Groups
            // change as the timeline does, and the expansion follows the events:
            // - growing (older history loaded, a new event appended) keeps a group expanded;
            // - merging (the event between two groups is redacted) expands the merged group when
            //   either part was expanded;
            // - splitting (an event lands inside a group) keeps every part expanded.
            // After each change the map is normalised to exactly the members of the expanded groups:
            // newcomers are added (so a later split stays consistent) and keys of events that are no
            // longer in an expanded group are dropped (so the map does not grow, and an event that
            // later joins a group again starts collapsed).
            val expandedSystemEvents = remember { mutableStateMapOf<String, Unit>() }
            fun isExpanded(group: TacitSystemEventGroup) = group.memberKeys.any { it in expandedSystemEvents }
            fun toggle(group: TacitSystemEventGroup) {
                if (isExpanded(group)) group.memberKeys.forEach { expandedSystemEvents.remove(it) }
                else group.memberKeys.forEach { expandedSystemEvents[it] = Unit }
            }
            LaunchedEffect(systemEventGroups) {
                snapshotFlow { systemEventGroups.value }.collect { groups ->
                    val expandedKeys = expandedSystemEvents.keys
                        .mapNotNullTo(HashSet()) { groups[it] }
                        .flatMapTo(HashSet()) { it.memberKeys }
                    expandedSystemEvents.keys.filter { it !in expandedKeys }.forEach { expandedSystemEvents.remove(it) }
                    expandedKeys.forEach { if (it !in expandedSystemEvents) expandedSystemEvents[it] = Unit }
                }
            }
            // Key of the event a jump unfolded its group for; that group opens without animation
            // until the jump has scrolled (see the scrollTo effect below).
            var snapOpenSystemEventKey by remember { mutableStateOf<String?>(null) }
            val isTimelineLoading = timelineViewElements.value.isEmpty()
            val error = timelineViewModel.error.collectAsState()
            val draggedFile = timelineViewModel.draggedFile.collectAsState()

            val focusManager = LocalFocusManager.current

            val showTypingIndicator =
                remember { timelineViewModel.canLoadAfter.throttleFirst(300.milliseconds).map { it == false } }
                    .collectAsState(false)

            val finishedScrollTo = remember { mutableStateOf<String?>(null) }
            val initialFirstVisibleItemIndex =
                getInitialFirstVisibleItemIndex(
                    timelineViewModel,
                    renderedTimelineViewElements,
                    showTypingIndicator,
                    finishedScrollTo,
                ).value
            Box(modifier = Modifier.weight(1.0f, fill = true)) {
                if (isTimelineLoading || (renderedTimelineViewElements.value.isNotEmpty() && initialFirstVisibleItemIndex == null)) {
                    Box(Modifier.fillMaxSize()) {
                        LoadingSpinner(Modifier.align(Alignment.Center))
                    }
                } else {
                    val listState =
                        rememberLazyListState(initialFirstVisibleItemIndex = initialFirstVisibleItemIndex ?: 0)
                    var initialAnchorApplied by remember { mutableStateOf(false) }
                    val tacitTimelineViewModel = timelineViewModel as? TacitTimelineViewModel

                    LaunchedEffect(initialFirstVisibleItemIndex, showTypingIndicator.value) {
                        if (!initialAnchorApplied) {
                            val targetIndex = initialFirstVisibleItemIndex ?: 0
                            if (targetIndex == 0) {
                                listState.scrollToItem(0)
                            } else {
                                listState.safeScrollIntoView(targetIndex)
                            }
                            initialAnchorApplied = true
                        }
                    }

                    LaunchedEffect(tacitTimelineViewModel, listState) {
                        tacitTimelineViewModel?.scrollToEndRequests?.collect {
                            listState.animateScrollToItem(0)
                        }
                    }

                    LaunchedEffect(scrollTo, showTypingIndicator.value) {
                        val scrollToKey = scrollTo ?: return@LaunchedEffect
                        // The requested element is not necessarily rendered yet; the view model may
                        // still be (re-)loading that part of the timeline. `indexOfFirst` does not
                        // suspend, so actually wait for the element to show up instead of giving up
                        // after the first look. The budget stays below the view model's own five
                        // second deadline so that the acknowledgement below still reaches it.
                        val index = withTimeoutOrNull(4.seconds) {
                            snapshotFlow { renderedTimelineViewElements.value.indexOfFirst { it.key == scrollToKey } }
                                .first { it >= 0 }
                        } ?: -1
                        val followsReplyPress = replyJumpSignal.consumeRecentPress()
                        // A jump to an event inside a collapsed system event group unfolds it, so the
                        // target is shown instead of a zero-height placeholder. Only the plain jump to
                        // the end (index 0, not following a reply press) leaves the groups alone.
                        val unfoldGroup = if (index > 0 || (index == 0 && followsReplyPress)) {
                            systemEventGroups.value[scrollToKey]?.takeIf { !isExpanded(it) }
                        } else {
                            null
                        }
                        if (unfoldGroup != null) {
                            // Snap the group open (no expand animation) and let the list compose and
                            // measure it before scrolling: scrolling during the animation would use
                            // the offsets of a half-open group and miss the target.
                            snapOpenSystemEventKey = scrollToKey
                            toggle(unfoldGroup)
                            withFrameNanos { }
                            withFrameNanos { }
                        }
                        when {
                            // Index 0 is the newest element, i.e. the request is "go to the end of
                            // the timeline" (that is what the jump-to-end button asks for). Pin to
                            // the very end instead of using `scrollIntoView`: the latter positions
                            // the item flush with the viewport start *including* the content
                            // padding, so the list stops a few pixels short, `isPinnedToEnd` never
                            // becomes true and the jump-to-end button stays on screen.
                            index == 0 -> listState.animateScrollToItem(0)
                            // Any other element is merely revealed, without jumping to the end.
                            index > 0 -> listState.safeScrollIntoView(if (showTypingIndicator.value) index + 1 else index)
                        }
                        snapOpenSystemEventKey = null
                        if (index > 0 && followsReplyPress) {
                            jumpHighlightKey = scrollToKey
                            jumpHighlightToken++
                        }
                        // The view model waits for this acknowledgement and warns ("could not
                        // scroll to ..., because UI did not set finishedScrollTo") when it never
                        // arrives. Acknowledge even when the element could not be found: there is
                        // nothing left to wait for in that case either.
                        finishedScrollTo.value = scrollToKey
                        scrollTo = null
                    }

                    val visibleItems = rememberVisibleItems(listState)
                    // Deliberately the unfiltered list: `updateVisibleItems` reports its first and
                    // last element as the loaded timeline bounds, and the view model only accepts a
                    // view state whose bounds match its own element list exactly. Passing the
                    // rendered list would report the wrong bounds whenever a redacted element sits
                    // at either end, and the view state (including `finishedScrollTo`) would then
                    // never reach the view model.
                    updateVisibleItems(timelineViewModel, visibleItems, timelineViewElements, finishedScrollTo)

                    val isPinnedToEnd = remember {
                        derivedStateOf {
                            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                            lastVisibleItem != null && lastVisibleItem.index == 0 && lastVisibleItem.offset == 0
                        }
                    }
                    // Caught up: the newest content is shown (nothing newer left to load, list pinned
                    // to the end) and the user can actually see it.
                    val windowInfo = LocalWindowInfo.current
                    LaunchedEffect(tacitTimelineViewModel, listState) {
                        snapshotFlow {
                            windowInfo.isWindowFocused && showTypingIndicator.value && isPinnedToEnd.value
                        }
                            .distinctUntilChanged()
                            .collect { tacitTimelineViewModel?.setCaughtUp(it) }
                    }
                    val newestTimelineKey = remember(renderedTimelineViewElements.value) {
                        renderedTimelineViewElements.value
                            .firstOrNull { it is TimelineViewElement.Element }
                            ?.key
                    }
                    var previousNewestTimelineKey by remember { mutableStateOf<String?>(null) }
                    var wasPinnedToEnd by remember { mutableStateOf(false) }

                    LaunchedEffect(listState) {
                        snapshotFlow { isPinnedToEnd.value }
                            .distinctUntilChanged()
                            .collect { pinnedToEnd ->
                                wasPinnedToEnd = pinnedToEnd
                            }
                    }

                    LaunchedEffect(newestTimelineKey, showTypingIndicator.value) {
                        val previousKey = previousNewestTimelineKey
                        previousNewestTimelineKey = newestTimelineKey
                        if (previousKey != null && newestTimelineKey != previousKey && wasPinnedToEnd) {
                            listState.animateScrollToItem(0)
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { focusManager.clearFocus(true) })
                            }
                    ) {
                        error.value?.let { errorMessage ->
                            ThemedModalDialog({ timelineViewModel.errorDismiss() }) {
                                ModalDialogHeader {
                                    Text(i18n.anErrorHasOccurred())
                                }
                                ModalDialogContent {
                                    Text(errorMessage)
                                }
                                ModalDialogFooter {
                                    ThemedButton(
                                        style = MaterialTheme.components.primaryButton,
                                        onClick = { timelineViewModel.errorDismiss() },
                                    ) {
                                        Text(i18n.actionOk())
                                    }
                                }
                            }
                        }
                        Box(Modifier.padding(vertical = 2.dp)) {
                            val canScrollToEnd = remember {
                                derivedStateOf { !isPinnedToEnd.value }
                            }
                            Box {
                                var focusedElement by remember(
                                    showTypingIndicator.value,
                                    renderedTimelineViewElements.value,
                                ) { mutableStateOf(0) }
                                // Elements that render nothing (Empty ones, collapsed group members) pass
                                // the roving focus on in the direction it last travelled, so it can move
                                // past a collapsed group towards newer elements as well.
                                val focusTravel = remember { TimelineFocusTravel() }
                                fun skipFocus(index: Int) {
                                    if (focusTravel.towardsNewer && index > 0) {
                                        focusedElement = index - 1
                                    } else {
                                        focusTravel.towardsNewer = false
                                        focusedElement = index + 1
                                    }
                                }
                                CompositionLocalProvider(
                                    LocalTacitReplyJumpSignal provides replyJumpSignal,
                                    LocalTacitJumpHighlightKey provides jumpHighlightKey,
                                ) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .rovingFocusContainer()
                                            .semantics {
                                                collectionInfo = CollectionInfo(1, renderedTimelineViewElements.value.size)
                                                liveRegion = LiveRegionMode.Polite
                                            },
                                        contentPadding = PaddingValues(
                                            top = 10.dp,
                                            bottom = 10.dp,
                                            start = timelineStartPadding,
                                            end = timelineEndPadding,
                                        ),
                                        state = listState,
                                        reverseLayout = true,
                                        verticalArrangement = Arrangement.Bottom,
                                    ) {
                                        if (showTypingIndicator.value) {
                                            item(key = "typing", contentType = "typing") {
                                                TypingIndicator(timelineViewModel)
                                                if (focusedElement == 0) focusedElement++
                                            }
                                        }
                                        itemsIndexed(
                                            items = renderedTimelineViewElements.value,
                                            key = { _, timelineViewElement -> timelineViewElement.key },
                                            contentType = { _, timelineViewElement ->
                                                when (timelineViewElement) {
                                                    is TimelineViewElement.Date -> "date"
                                                    is TimelineViewElement.Element -> "element"
                                                }
                                            },
                                        ) { index, timelineViewElement ->
                                            // Member of a group that a jump snaps open (no animations).
                                            val snapOpen = snapOpenSystemEventKey?.let { key ->
                                                systemEventGroups.value[timelineViewElement.key]?.memberKeys?.contains(key)
                                            } == true
                                            Box(
                                                Modifier
                                                    .rovingFocusItem(
                                                        isFocused = focusedElement == index,
                                                        onFocus = {
                                                            focusTravel.towardsNewer = index < focusedElement
                                                            focusedElement = index
                                                        },
                                                    )
                                                    .animateItem()
                                                    .then(if (snapOpen) Modifier else Modifier.animateContentSize())
                                            ) {
                                                when (timelineViewElement) {
                                                    is TimelineViewElement.Date -> {
                                                        DateStickyHeader(
                                                            date = timelineViewElement.formattedDate,
                                                            focusable = true,
                                                        )
                                                    }

                                                    is TimelineViewElement.Element -> {
                                                        val viewModel = timelineViewElement.viewModel
                                                        if (viewModel.element.value is TimelineElementViewModel.Empty && index == focusedElement) {
                                                            skipFocus(index)
                                                        }

                                                        val groups = systemEventGroups.value
                                                        val group = groups[viewModel.key]
                                                        if (viewModel is TimelineElementHolderViewModel && groups.isSystemEvent(viewModel.key)) {
                                                            val expanded = group != null && isExpanded(group)
                                                            // Collapsed members other than the head render nothing; keep
                                                            // keyboard focus moving like for Empty elements.
                                                            if (group != null && !expanded && group.headKey != viewModel.key && index == focusedElement) {
                                                                skipFocus(index)
                                                            }
                                                            TacitSystemEventHolder(
                                                                holder = viewModel,
                                                                index = index,
                                                                group = group,
                                                                expanded = expanded,
                                                                snapOpen = snapOpen,
                                                                onToggle = { group?.let { toggle(it) } },
                                                            )
                                                        } else {
                                                            TimelineElementHolder(viewModel, index)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                FloatingDateHeader(
                                    visible = visibleItems,
                                    timelineViewElements = renderedTimelineViewElements,
                                    show = listState.canScrollForward,
                                )
                                ScrollToEndButton(timelineViewModel, canScrollToEnd)
                                draggedFile.value?.let { draggedFileValue ->
                                    Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Filled.Circle,
                                                    contentDescription = "",
                                                    modifier = Modifier.size(100.dp),
                                                    tint = Color.Gray,
                                                )
                                                Icon(
                                                    imageVector = MaterialTheme.messengerIcons.attachFile,
                                                    contentDescription = i18n.timelineSendFile(),
                                                    modifier = Modifier.size(60.dp),
                                                )
                                            }
                                            Text(
                                                // Not toString(): the desktop implementation
                                                // (PathFileDescriptor) is a plain class, so that renders
                                                // an object reference rather than the file's name.
                                                text = draggedFileValue.fileName,
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                        }
                                    }
                                }

                                ReportMessageSwitch(timelineViewModel)
                            }

                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                lazyListState = listState,
                                reverseLayout = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating date indicator drawn on top of the timeline.
 *
 * Replaces the upstream `ListDateHeader`, which renders a bare (and partly translucent) date pill
 * directly over the list. Because that pill is narrower than centred timeline items such as
 * membership notices, whatever scrolls beneath it pokes out around its edges and interleaves with
 * the date text. Here the pill is backed by a full-width, opaque strip in the timeline's own
 * surface colour, followed by a short fade, so content scrolling underneath is hidden completely
 * inside the strip and reappears gradually below it.
 */
@Composable
private fun FloatingDateHeader(
    visible: State<Pair<String, String>?>,
    timelineViewElements: State<List<TimelineViewElement>>,
    show: Boolean,
) {
    if (!show) return

    val formattedDate = remember(visible, timelineViewElements) {
        derivedStateOf {
            visible.value?.first?.let { topMostVisibleKey ->
                timelineViewElements.value
                    .asSequence()
                    .filterIsInstance<TimelineViewElement.Element>()
                    .firstOrNull { it.viewModel.key == topMostVisibleKey }
                    ?.viewModel
                    ?.formattedDate
            }
        }
    }
    val date = formattedDate.value ?: return

    // The timeline itself is drawn on `components.timeline`, which is `colorScheme.surface`.
    val backdropColor = MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().background(backdropColor)) {
            // Keep the pill centred over the message column, which is inset by the scrollbar gutter.
            Box(Modifier.padding(end = additionalEndPadding.dp)) {
                DateStickyHeader(date = date, focusable = false)
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(floatingDateHeaderFadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(backdropColor, backdropColor.copy(alpha = 0f)),
                    )
                )
        )
    }
}
