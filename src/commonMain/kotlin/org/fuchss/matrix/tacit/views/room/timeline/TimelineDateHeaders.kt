package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.room.timeline.DateStickyHeader
import de.connect2x.trixnity.messenger.compose.view.room.timeline.TimelineViewElement
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.RedactedTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementViewModel

/** Height of the soft fade below the floating date header's opaque backdrop. */
private val floatingDateHeaderFadeHeight = 16.dp

/**
 * Inserts a date header before (visually: above) every item whose date differs from the one of the
 * previous dated item, walking [itemsNewestFirst] oldest first; the result is newest first again.
 * Items that render nothing ([rendersNothing]) never get a header and do not count as the previous
 * dated item, so a date change hidden behind them still shows a header above the next visible one.
 */
internal fun <T, R> insertDateHeaders(
    itemsNewestFirst: List<T>,
    dateOf: (T) -> String?,
    rendersNothing: (T) -> Boolean,
    header: (T) -> R,
    element: (T) -> R,
): List<R> =
    buildList(itemsNewestFirst.size * 2) {
        var lastDate: String? = null
        for (item in itemsNewestFirst.asReversed()) {
            val date = dateOf(item)
            when {
                lastDate == date -> add(element(item))
                rendersNothing(item) -> add(element(item))
                else -> {
                    add(header(item))
                    add(element(item))
                    lastDate = date
                }
            }
        }
    }.asReversed()

/**
 * The elements the timeline renders: upstream's elements without the redacted ones (Tacit hides
 * those), with the date headers recomputed so that a hidden element does not leave a stray header.
 */
internal fun buildRenderableTimelineElements(timelineViewElements: List<TimelineViewElement>): List<TimelineViewElement> =
    insertDateHeaders(
        itemsNewestFirst = timelineViewElements
            .filterIsInstance<TimelineViewElement.Element>()
            .map { it.viewModel }
            .filterNot { it.element.value is RedactedTimelineElementViewModel },
        dateOf = { it.formattedDate },
        rendersNothing = { it.element.value is TimelineElementViewModel.Empty },
        header = { TimelineViewElement.Date(it) },
        element = { TimelineViewElement.Element(it) },
    )

/**
 * Floating date indicator drawn on top of the timeline.
 *
 * Replaces the upstream `ListDateHeader`, which renders a bare (and partly translucent) date pill
 * directly over the list. Because that pill is narrower than centred timeline items such as
 * membership notices, whatever scrolls beneath it pokes out around its edges and interleaves with
 * the date text. Here the pill is backed by a full-width, opaque strip in the timeline's own
 * surface colour, followed by a short fade, so content scrolling underneath is hidden completely
 * inside the strip and reappears gradually below it.
 *
 * [endPadding] keeps the pill centred over the message column, which is inset by the scrollbar gutter.
 */
@Composable
internal fun FloatingDateHeader(
    visible: State<Pair<String, String>?>,
    timelineViewElements: State<List<TimelineViewElement>>,
    show: Boolean,
    endPadding: Dp,
    modifier: Modifier = Modifier,
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
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().background(backdropColor)) {
            Box(Modifier.padding(end = endPadding)) {
                WithoutTooltips { DateStickyHeader(date = date, focusable = false) }
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
