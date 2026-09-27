package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.RoomSwitch
import de.connect2x.trixnity.messenger.compose.view.roomlist.RoomListSwitch
import de.connect2x.trixnity.messenger.compose.view.root.MessengerView
import de.connect2x.trixnity.messenger.viewmodel.MainViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.RoomRouter
import de.connect2x.trixnity.messenger.viewmodel.util.toFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.settings.readTacitRoomListWidthDp
import org.fuchss.matrix.tacit.settings.writeTacitRoomListWidthDp
import org.fuchss.matrix.tacit.tacitBorder
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.ui.TacitSpacing

/*
 * The room list column carries a lot of fixed chrome before a single character of a room name
 * can be drawn. [minRoomListWidth] is derived from those real components instead of being
 * guessed, so that the "minimum" is actually usable:
 *
 *   outer app padding      TacitRoomListView   2 * TacitSpacing.appPadding        = 20.dp
 *   guild rail             GuildRail                84.dp                         = 84.dp
 *   gap rail <-> pane      TacitRoomListView   TacitSpacing.paneGap               = 10.dp
 *   pane frame padding     TacitPaneSurface    2 * TacitSpacing.paneFramePadding  = 12.dp
 *   room list body padding RoomListBody        2 * 8.dp                           = 16.dp
 *   channel row padding    RoomListChannels    2 * 4.dp + 2 * 10.dp               = 28.dp
 *                                                                          chrome = 170.dp
 *   avatar slot + gap      RoomListChannels         40.dp + 8.dp                  = 48.dp
 *   timestamp + gap        RoomListChannels         44.dp + 6.dp                  = 50.dp
 *   trailing indicator     RoomListChannels         24.dp + 4.dp                  = 28.dp
 *                                                                      row fixed  = 126.dp
 *   remaining for the room name / last message preview                            = 148.dp
 */
private val roomListChrome: Dp =
    (TacitSpacing.appPadding * 2) + // TacitRoomListView outer padding
        84.dp + // GuildRail
        TacitSpacing.paneGap +
        (TacitSpacing.paneFramePadding * 2) + // TacitPaneSurface
        16.dp + // RoomListBody horizontal padding
        28.dp // ChannelRow outer + inner horizontal padding

private val roomListRowFixedContent: Dp =
    48.dp + // avatar slot + gap
        50.dp + // timestamp + gap
        28.dp // trailing unread / badge indicator

private val roomListMinTextWidth = 148.dp

internal val minRoomListWidth: Dp = roomListChrome + roomListRowFixedContent + roomListMinTextWidth // 444.dp

private val minRoomWidth = 420.dp
private val splitterWidth = 10.dp
private val defaultRoomListWidth = 480.dp

/** Smallest window size at which the single pane layout is still usable. */
internal val minTacitWindowWidth: Dp = minRoomListWidth
internal val minTacitWindowHeight = 600.dp

val LocalTacitRoomListHidden = compositionLocalOf { false }

class TacitMessengerView : MessengerView {
    @Composable
    override fun create(mainViewModel: MainViewModel, isSinglePane: Boolean) {
        val isRoomShown = remember {
            mainViewModel.roomRouterStack.toFlow().map { it.active.configuration !is RoomRouter.Config.None }
        }.collectAsState(initial = false).value

        // Both layout branches below render the very same room list / room subtree. Wrapping them
        // in movable content lets Compose *move* the subtree when the breakpoint is crossed instead
        // of disposing and recreating it, which would reset scroll position, filters and search.
        val roomListContent = remember(mainViewModel) {
            movableContentOf { RoomListSwitch(mainViewModel) }
        }
        val roomContent = remember(mainViewModel) {
            movableContentOf { RoomSwitch(mainViewModel.roomRouterStack) }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val splitterWidthPx = with(density) { splitterWidth.toPx() }
            val minRoomListWidthPx = with(density) { minRoomListWidth.toPx() }
            val minRoomWidthPx = with(density) { minRoomWidth.toPx() }
            val minTwoPaneWidthPx = minRoomListWidthPx + minRoomWidthPx + splitterWidthPx
            val canShowTwoPanes = maxWidthPx >= minTwoPaneWidthPx

            if (!canShowTwoPanes) {
                if (isRoomShown) {
                    CompositionLocalProvider(LocalTacitRoomListHidden provides true) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            roomContent()
                        }
                    }
                } else {
                    CompositionLocalProvider(LocalTacitRoomListHidden provides false) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            roomListContent()
                        }
                    }
                }
                return@BoxWithConstraints
            }

            val settings = DI.get<MatrixMessengerSettingsHolder>()
            val scope = rememberCoroutineScope()

            // The *intent*: where the user dragged the splitter to, in dp, never clamped.
            // Keeping it unclamped is what makes dragging back from a limit react immediately
            // instead of waiting for the cursor to "catch up" with the rendered width.
            var roomListWidthIntentDp by remember {
                mutableFloatStateOf(
                    readTacitRoomListWidthDp(settings.value) ?: defaultRoomListWidth.value
                )
            }

            val totalContentWidthPx = (maxWidthPx - splitterWidthPx).coerceAtLeast(1f)
            val maxRoomListWidthPx = (totalContentWidthPx - minRoomWidthPx).coerceAtLeast(minRoomListWidthPx)

            // The *rendering*: the absolute width is kept as the window is resized; it is only
            // squeezed when the window becomes too narrow to keep the room pane above its minimum.
            val roomListWidthDp = with(density) {
                roomListWidthIntentDp.dp.toPx().coerceIn(minRoomListWidthPx, maxRoomListWidthPx).toDp()
            }

            fun persistRoomListWidth() {
                val widthToPersist = roomListWidthDp.value
                scope.launch {
                    settings.update { writeTacitRoomListWidthDp(widthToPersist) }
                }
            }

            val splitterDragState = rememberDraggableState { deltaPx ->
                // Advance the never-clamped accumulator by the raw delta. The clamp lives in
                // roomListWidthDp only and is never written back here.
                roomListWidthIntentDp += with(density) { deltaPx.toDp() }.value
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .width(roomListWidthDp)
                        .fillMaxHeight()
                ) {
                    roomListContent()
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(splitterWidth)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    roomListWidthIntentDp = defaultRoomListWidth.value
                                    persistRoomListWidth()
                                },
                            )
                        }
                        .draggable(
                            state = splitterDragState,
                            orientation = Orientation.Horizontal,
                            onDragStopped = { persistRoomListWidth() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 18.dp)
                            .width(1.dp)
                            .background(tacitBorder.copy(alpha = 0.22f), TacitShapes.pill)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 5.dp, height = 44.dp)
                            .background(tacitBorder.copy(alpha = 0.68f), TacitShapes.pill)
                    )
                }

                CompositionLocalProvider(LocalTacitRoomListHidden provides false) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isRoomShown) {
                            roomContent()
                        }
                    }
                }
            }
        }
    }
}
