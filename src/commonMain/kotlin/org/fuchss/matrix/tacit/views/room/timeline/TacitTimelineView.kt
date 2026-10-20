package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.util.throttleFirst
import kotlinx.coroutines.flow.map
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitTimelineViewModel
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import kotlin.time.Duration.Companion.milliseconds

private const val additionalEndPadding = 8
private val timelineStartPadding = 10.dp
private val timelineEndPadding = (10 + additionalEndPadding).dp

private val timelineItemKey: (Int, TimelineViewElement) -> Any = { _, timelineViewElement -> timelineViewElement.key }
private val timelineItemContentType: (Int, TimelineViewElement) -> Any? = { _, timelineViewElement ->
    when (timelineViewElement) {
        is TimelineViewElement.Date -> "date"
        is TimelineViewElement.Element -> "element"
    }
}

class TacitTimelineView : TimelineView {
    @Composable
    override fun ColumnScope.create(timelineViewModel: TimelineViewModel) {
        key(timelineViewModel) {
            val timelineViewElements = rememberTimelineViewElements(timelineViewModel)
            val renderedTimelineViewElements = remember(timelineViewElements) {
                derivedStateOf { buildRenderableTimelineElements(timelineViewElements.value) }
            }
            // Runs of consecutive system events collapse into one expandable row (see
            // TacitSystemEventGroups). Grouping happens per element, not by merging lazy items: every
            // element keeps its own item, key and index, so scroll anchoring, index-based scrolling,
            // visible-item reporting (read receipts, read marker) and the unread marker are untouched.
            val systemEventGroups = rememberTacitSystemEventGroups(timelineViewElements)
            val expansion = rememberSystemEventExpansionState(systemEventGroups)
            val jumpHighlight = rememberJumpHighlightState()
            val scrollRequests = rememberTimelineScrollRequests(timelineViewModel)
            val showTypingIndicator =
                remember(timelineViewModel) { timelineViewModel.canLoadAfter.throttleFirst(300.milliseconds).map { it == false } }
                    .collectAsState(false)
            val initialFirstVisibleItemIndex =
                getInitialFirstVisibleItemIndex(
                    timelineViewModel,
                    renderedTimelineViewElements,
                    showTypingIndicator,
                    scrollRequests.finished,
                ).value
            val isTimelineLoading = timelineViewElements.value.isEmpty()
            Box(modifier = Modifier.weight(1.0f, fill = true)) {
                if (isTimelineLoading || (renderedTimelineViewElements.value.isNotEmpty() && initialFirstVisibleItemIndex == null)) {
                    Box(Modifier.fillMaxSize()) {
                        LoadingSpinner(Modifier.align(Alignment.Center))
                    }
                } else {
                    TimelineContent(
                        timelineViewModel = timelineViewModel,
                        timelineViewElements = timelineViewElements,
                        renderedTimelineViewElements = renderedTimelineViewElements,
                        systemEventGroups = systemEventGroups,
                        expansion = expansion,
                        jumpHighlight = jumpHighlight,
                        scrollRequests = scrollRequests,
                        showTypingIndicator = showTypingIndicator,
                        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
                    )
                }
            }
        }
    }
}

/** The loaded timeline: the list with its overlays (date header, jump-to-end button, dragged file) and scrollbar. */
@Composable
private fun TimelineContent(
    timelineViewModel: TimelineViewModel,
    timelineViewElements: State<List<TimelineViewElement>>,
    renderedTimelineViewElements: State<List<TimelineViewElement>>,
    systemEventGroups: State<TacitSystemEventGroups>,
    expansion: SystemEventExpansionState,
    jumpHighlight: JumpHighlightState,
    scrollRequests: TimelineScrollRequests,
    showTypingIndicator: State<Boolean>,
    initialFirstVisibleItemIndex: Int?,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialFirstVisibleItemIndex ?: 0)
    val tacitTimelineViewModel = timelineViewModel as? TacitTimelineViewModel
    val scrollState = rememberTimelineScrollState(listState, tacitTimelineViewModel, initialFirstVisibleItemIndex, showTypingIndicator)
    TimelineJumpEffect(
        requests = scrollRequests,
        scrollState = scrollState,
        rendered = renderedTimelineViewElements,
        groups = systemEventGroups,
        expansion = expansion,
        highlight = jumpHighlight,
        showTypingIndicator = showTypingIndicator,
    )

    val visibleItems = rememberVisibleItems(listState)
    // Deliberately the unfiltered list: `updateVisibleItems` reports its first and last element as the
    // loaded timeline bounds, and the view model only accepts a view state whose bounds match its own
    // element list exactly. Passing the rendered list would report the wrong bounds whenever a
    // redacted element sits at either end, and the view state (including `finishedScrollTo`) would
    // then never reach the view model.
    updateVisibleItems(timelineViewModel, visibleItems, timelineViewElements, scrollRequests.finished)

    val newestTimelineKey = remember(renderedTimelineViewElements.value) {
        renderedTimelineViewElements.value.firstOrNull { it is TimelineViewElement.Element }?.key
    }
    TimelineFollowEffects(scrollState, tacitTimelineViewModel, showTypingIndicator, newestTimelineKey)

    val focusManager = LocalFocusManager.current
    BoxWithConstraints(
        modifier = Modifier.pointerInput(focusManager) {
            detectTapGestures(onTap = { focusManager.clearFocus(true) })
        }
    ) {
        TimelineErrorDialog(timelineViewModel)
        Box(Modifier.padding(vertical = 2.dp)) {
            Box {
                TimelineList(
                    timelineViewModel = timelineViewModel,
                    listState = listState,
                    renderedTimelineViewElements = renderedTimelineViewElements,
                    systemEventGroups = systemEventGroups,
                    expansion = expansion,
                    jumpHighlight = jumpHighlight,
                    showTypingIndicator = showTypingIndicator.value,
                    modifier = Modifier.fillMaxSize(),
                )
                FloatingDateHeader(
                    visible = visibleItems,
                    timelineViewElements = renderedTimelineViewElements,
                    show = listState.canScrollForward,
                    endPadding = additionalEndPadding.dp,
                )
                ScrollToEndButton(timelineViewModel, scrollState.canScrollToEnd)
                DraggedFileOverlay(timelineViewModel, Modifier.fillMaxSize())
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

@Composable
private fun TimelineList(
    timelineViewModel: TimelineViewModel,
    listState: LazyListState,
    renderedTimelineViewElements: State<List<TimelineViewElement>>,
    systemEventGroups: State<TacitSystemEventGroups>,
    expansion: SystemEventExpansionState,
    jumpHighlight: JumpHighlightState,
    showTypingIndicator: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTimelineFocusState(showTypingIndicator, renderedTimelineViewElements.value)
    CompositionLocalProvider(
        LocalTacitReplyJumpSignal provides jumpHighlight.replyJumpSignal,
        LocalTacitJumpHighlightKey provides jumpHighlight.key,
    ) {
        LazyColumn(
            modifier = modifier
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
            if (showTypingIndicator) {
                item(key = "typing", contentType = "typing") {
                    TypingIndicator(timelineViewModel)
                    if (focusState.isFocused(0)) {
                        LaunchedEffect(focusState) { focusState.skipTypingIndicator() }
                    }
                }
            }
            itemsIndexed(
                items = renderedTimelineViewElements.value,
                key = timelineItemKey,
                contentType = timelineItemContentType,
            ) { index, timelineViewElement ->
                TimelineItem(timelineViewElement, index, focusState, systemEventGroups, expansion)
            }
        }
    }
}

@Composable
private fun LazyItemScope.TimelineItem(
    timelineViewElement: TimelineViewElement,
    index: Int,
    focusState: TimelineFocusState,
    systemEventGroups: State<TacitSystemEventGroups>,
    expansion: SystemEventExpansionState,
) {
    val groups = systemEventGroups.value
    // Member of a group that a jump snaps open (no animations).
    val snapOpen = expansion.isSnapOpen(timelineViewElement.key, groups)
    val onFocus = remember(focusState, index) { { focusState.onFocus(index) } }
    Box(
        Modifier
            .rovingFocusItem(isFocused = focusState.isFocused(index), onFocus = onFocus)
            .animateItem()
            .then(if (snapOpen) Modifier else Modifier.animateContentSize())
    ) {
        when (timelineViewElement) {
            is TimelineViewElement.Date -> WithoutTooltips {
                DateStickyHeader(date = timelineViewElement.formattedDate, focusable = true)
            }

            is TimelineViewElement.Element ->
                TimelineElement(timelineViewElement.viewModel, index, focusState, groups, expansion, snapOpen)
        }
    }
}

@Composable
private fun TimelineElement(
    viewModel: BaseTimelineElementHolderViewModel,
    index: Int,
    focusState: TimelineFocusState,
    groups: TacitSystemEventGroups,
    expansion: SystemEventExpansionState,
    snapOpen: Boolean,
) {
    val systemEvent = (viewModel as? TimelineElementHolderViewModel)?.takeIf { groups.isSystemEvent(it.key) }
    val group = groups[viewModel.key]
    val expanded = systemEvent != null && group != null && expansion.isExpanded(group)
    // Elements that render nothing (Empty ones, collapsed group members other than the head) pass
    // the roving focus on (see TimelineFocusState).
    val rendersNothing = rendersNothingInTimeline(
        isEmpty = viewModel.element.value is TimelineElementViewModel.Empty,
        isGroupedSystemEvent = systemEvent != null && group != null,
        groupExpanded = expanded,
        isGroupHead = group?.headKey == viewModel.key,
    )
    if (rendersNothing && focusState.isFocused(index)) {
        LaunchedEffect(focusState, index) { focusState.skipFrom(index) }
    }

    if (systemEvent != null) {
        val onToggle = remember(expansion, group) { { if (group != null) expansion.toggle(group) } }
        TacitSystemEventHolder(
            holder = systemEvent,
            index = index,
            group = group,
            expanded = expanded,
            snapOpen = snapOpen,
            onToggle = onToggle,
        )
    } else {
        CompositionLocalProvider(LocalTacitFollowsSystemEvent provides groups.followsSystemEvent(viewModel.key)) {
            TimelineElementHolder(viewModel, index)
        }
    }
}

@Composable
private fun TimelineErrorDialog(timelineViewModel: TimelineViewModel) {
    val errorMessage = timelineViewModel.error.collectAsState().value ?: return
    val i18n = DI.get<TacitI18nView>()
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

/** The drop hint while a file is dragged over the timeline. */
@Composable
private fun DraggedFileOverlay(timelineViewModel: TimelineViewModel, modifier: Modifier = Modifier) {
    val draggedFile = timelineViewModel.draggedFile.collectAsState().value ?: return
    val i18n = DI.get<TacitI18nView>()
    Box(modifier, contentAlignment = Alignment.Center) {
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
                // Not toString(): the desktop implementation (PathFileDescriptor) is a plain class,
                // so that renders an object reference rather than the file's name.
                text = draggedFile.fileName,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
