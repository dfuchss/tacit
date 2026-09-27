package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.util.throttleFirst
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

class TacitTimelineView : TimelineView {
    @Composable
    override fun ColumnScope.create(timelineViewModel: TimelineViewModel) {
        key(timelineViewModel) {
            val i18n = DI.get<TacitI18nView>()
            var scrollTo by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(timelineViewModel) {
                timelineViewModel.scrollTo.drop(1).collect { scrollTo = it }
            }

            val timelineViewElements = rememberTimelineViewElements(timelineViewModel)
            val renderedTimelineViewElements = remember(timelineViewElements.value) {
                derivedStateOf {
                    buildRenderableTimelineElements(timelineViewElements.value)
                }
            }
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
                                listState.scrollIntoView(targetIndex)
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
                        when {
                            // Index 0 is the newest element, i.e. the request is "go to the end of
                            // the timeline" (that is what the jump-to-end button asks for). Pin to
                            // the very end instead of using `scrollIntoView`: the latter positions
                            // the item flush with the viewport start *including* the content
                            // padding, so the list stops a few pixels short, `isPinnedToEnd` never
                            // becomes true and the jump-to-end button stays on screen.
                            index == 0 -> listState.animateScrollToItem(0)
                            // Any other element is merely revealed, without jumping to the end.
                            index > 0 -> listState.scrollIntoView(if (showTypingIndicator.value) index + 1 else index)
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
                                        Box(
                                            Modifier
                                                .rovingFocusItem(
                                                    isFocused = focusedElement == index,
                                                    onFocus = { focusedElement = index },
                                                )
                                                .animateItem()
                                                .animateContentSize()
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
                                                        focusedElement++
                                                    }

                                                    TimelineElementHolder(viewModel, index)
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
                                                text = draggedFileValue.toString(),
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
