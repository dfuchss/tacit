package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.animation.animateContentSize
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
import kotlinx.coroutines.withTimeoutOrNull
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val additionalEndPadding = 8
private val timelineStartPadding = 10.dp
private val timelineEndPadding = (10 + additionalEndPadding).dp

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
                remember { timelineViewModel.canLoadAfter.throttleFirst(300.milliseconds) }
                    .collectAsState(false).value == false

            val initialFirstVisibleItemIndex =
                getInitialFirstVisibleItemIndex(
                    timelineViewModel,
                    renderedTimelineViewElements.value,
                    showTypingIndicator,
                )
            Box(modifier = Modifier.weight(1.0f, fill = true)) {
                if (isTimelineLoading || (renderedTimelineViewElements.value.isNotEmpty() && initialFirstVisibleItemIndex == null)) {
                    Box(Modifier.fillMaxSize()) {
                        LoadingSpinner(Modifier.align(Alignment.Center))
                    }
                } else {
                    val listState =
                        rememberLazyListState(initialFirstVisibleItemIndex = initialFirstVisibleItemIndex ?: 0)
                    var initialAnchorApplied by remember { mutableStateOf(false) }

                    LaunchedEffect(initialFirstVisibleItemIndex, showTypingIndicator) {
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

                    LaunchedEffect(scrollTo, renderedTimelineViewElements.value, showTypingIndicator) {
                        if (scrollTo != null) {
                            val index = withTimeoutOrNull(5.seconds) {
                                renderedTimelineViewElements.value.indexOfFirst { it.key == scrollTo }
                            } ?: -1
                            if (index >= 0) {
                                listState.scrollIntoView(
                                    when {
                                        index == 0 && showTypingIndicator -> 0
                                        showTypingIndicator -> index + 1
                                        else -> index
                                    }
                                )
                                scrollTo = null
                            }
                        }
                    }

                    val visibleItems = rememberVisibleItems(listState)
                    updateVisibleItems(timelineViewModel, visibleItems, renderedTimelineViewElements)

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

                    LaunchedEffect(newestTimelineKey, showTypingIndicator) {
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
                                    showTypingIndicator,
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
                                    if (showTypingIndicator) {
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
                                ListDateHeader(
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
