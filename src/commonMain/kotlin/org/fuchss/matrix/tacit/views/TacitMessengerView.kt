package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.room.RoomSwitch
import de.connect2x.trixnity.messenger.compose.view.roomlist.RoomListSwitch
import de.connect2x.trixnity.messenger.compose.view.root.MessengerView
import de.connect2x.trixnity.messenger.viewmodel.MainViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.RoomRouter
import de.connect2x.trixnity.messenger.viewmodel.util.toFlow
import kotlinx.coroutines.flow.map
import org.fuchss.matrix.tacit.tacitBorder

private val minRoomListWidth = 340.dp
private val minRoomWidth = 420.dp
private val splitterWidth = 10.dp
private const val defaultRoomListRatio = 0.30f

val LocalTacitRoomListHidden = compositionLocalOf { false }

class TacitMessengerView : MessengerView {
    @Composable
    override fun create(mainViewModel: MainViewModel, isSinglePane: Boolean) {
        val isRoomShown = remember {
            mainViewModel.roomRouterStack.toFlow().map { it.active.configuration !is RoomRouter.Config.None }
        }.collectAsState(initial = false).value

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
                            RoomSwitch(mainViewModel.roomRouterStack)
                        }
                    }
                } else {
                    CompositionLocalProvider(LocalTacitRoomListHidden provides false) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            RoomListSwitch(mainViewModel)
                        }
                    }
                }
                return@BoxWithConstraints
            }

            val totalContentWidthPx = (maxWidthPx - splitterWidthPx).coerceAtLeast(1f)
            val minRatio = (minRoomListWidthPx / totalContentWidthPx).coerceIn(0f, 1f)
            val maxRatio = ((totalContentWidthPx - minRoomWidthPx) / totalContentWidthPx).coerceIn(minRatio, 1f)

            var roomListRatio by remember { mutableFloatStateOf(defaultRoomListRatio) }
            val clampedRatio = roomListRatio.coerceIn(minRatio, maxRatio)

            val roomListWidthDp = with(density) { (totalContentWidthPx * clampedRatio).toDp() }

            val splitterDragState = rememberDraggableState { deltaPx ->
                val currentRoomListWidth = totalContentWidthPx * clampedRatio
                val newRoomListWidth = (currentRoomListWidth + deltaPx)
                    .coerceIn(minRoomListWidthPx, totalContentWidthPx - minRoomWidthPx)
                roomListRatio = newRoomListWidth / totalContentWidthPx
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .width(roomListWidthDp)
                        .fillMaxHeight()
                ) {
                    RoomListSwitch(mainViewModel)
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(splitterWidth)
                        .draggable(
                            state = splitterDragState,
                            orientation = Orientation.Horizontal,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 14.dp)
                            .width(1.dp)
                            .background(tacitBorder.copy(alpha = 0.30f), RoundedCornerShape(50))
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 34.dp)
                            .background(tacitBorder.copy(alpha = 0.62f), RoundedCornerShape(50))
                    )
                }

                CompositionLocalProvider(LocalTacitRoomListHidden provides false) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isRoomShown) {
                            RoomSwitch(mainViewModel.roomRouterStack)
                        }
                    }
                }
            }
        }
    }
}
