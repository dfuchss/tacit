package org.fuchss.matrix.tacit.views.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.RoomView
import de.connect2x.trixnity.messenger.compose.view.room.SETTINGS_WEIGHT
import de.connect2x.trixnity.messenger.compose.view.room.TIMELINE_WEIGHT
import de.connect2x.trixnity.messenger.compose.view.room.settings.ExtrasPaneContentSwitch
import de.connect2x.trixnity.messenger.compose.view.room.timeline.RoomContentSwitch
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedSurface
import de.connect2x.trixnity.messenger.viewmodel.room.RoomViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.settings.ExtrasRouter
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineRouter
import de.connect2x.trixnity.messenger.viewmodel.util.toFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.TacitRoomNavigationState
import org.fuchss.matrix.tacit.tacitBackground
import org.fuchss.matrix.tacit.tacitSurface
import org.fuchss.matrix.tacit.tacitTextMuted
import org.fuchss.matrix.tacit.ui.TacitPaneSurface
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.ui.TacitSpacing
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitTimelineViewModel
import org.fuchss.matrix.tacit.views.LocalTacitRoomListHidden
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

/**
 * Threshold for splitting the *room pane* into timeline + extras/settings.
 *
 * Upstream's `TWO_PANE_THRESHOLD` (1100.dp) describes a whole window, not a single pane. Applied to
 * the room pane it only ever triggered at roughly 1600.dp+ window width and only when the room list
 * splitter happened to sit far enough left, so opening the room settings sometimes appeared next to
 * the chat and sometimes replaced it. This threshold is proportionate to Tacit's own `minRoomWidth`
 * (420.dp): at 760.dp the timeline keeps ~456.dp (TIMELINE_WEIGHT = 0.6) and the settings pane gets
 * ~304.dp, which is still a workable settings column.
 */
private val roomExtrasTwoPaneThreshold = 760.dp

class TacitRoomView : RoomView {
    @Composable
    override fun create(roomViewModel: RoomViewModel) {
        val roomListHiddenForSize = LocalTacitRoomListHidden.current
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(tacitBackground)
                .padding(TacitSpacing.appPadding)
        ) {
            val isSinglePane = this@BoxWithConstraints.maxWidth < roomExtrasTwoPaneThreshold

            val isSettingsShown = remember {
                roomViewModel.extrasStack.toFlow().map { it.active.configuration is ExtrasRouter.Config.RoomSettings }
            }.collectAsState(initial = false).value
            val isExtrasShown = remember {
                roomViewModel.extrasStack.toFlow().map { it.active.configuration !is ExtrasRouter.Config.None }
            }.collectAsState(initial = false).value
            val activeTimelineStack = roomViewModel.timelineStack.subscribeAsState().value
            val activeTimelineConfig = activeTimelineStack.active.configuration as? TimelineRouter.Config.View
            val activeTimelineViewModel =
                (activeTimelineStack.active.instance as? TimelineRouter.Wrapper.View)?.viewModel
            val tacitTimelineViewModel = activeTimelineViewModel as? TacitTimelineViewModel
            val activeRoomId = activeTimelineConfig?.roomId
            val pendingSettingsRoomId = TacitRoomNavigationState.openSettingsForRoomId
            val pendingCloseRoomAfterSettings = TacitRoomNavigationState.closeRoomAfterSettingsForRoomId

            LaunchedEffect(activeRoomId, pendingSettingsRoomId) {
                if (pendingSettingsRoomId != null && pendingSettingsRoomId == activeRoomId) {
                    TacitRoomNavigationState.openSettingsForRoomId = null
                    roomViewModel.openRoomSettings()
                }
            }
            LaunchedEffect(activeRoomId, pendingCloseRoomAfterSettings, isExtrasShown) {
                if (
                    pendingCloseRoomAfterSettings != null &&
                    pendingCloseRoomAfterSettings == activeRoomId &&
                    !isExtrasShown
                ) {
                    TacitRoomNavigationState.closeRoomAfterSettingsForRoomId = null
                    TacitRoomNavigationState.suppressBackButtonForRoomId = null
                    roomViewModel.closeRoom()
                }
            }

            val roomMembers = tacitTimelineViewModel?.roomMembers?.collectAsState()?.value.orEmpty()
            val scope = rememberCoroutineScope()
            val membersPaneRequested = TacitRoomNavigationState.showMembersPane
            val showMembersPane =
                membersPaneRequested && !isExtrasShown && activeRoomId != null && tacitTimelineViewModel != null
            val membersPaneWidth = if (isSinglePane) 248.dp else 272.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (!isExtrasShown || !isSinglePane) {
                    ModernPane(
                        modifier = Modifier.weight(if (isSinglePane) 1F else TIMELINE_WEIGHT),
                        content = {
                            TimelinePane(
                                roomViewModel = roomViewModel,
                                showSettingsButton = !isSettingsShown,
                                showBackButton = roomListHiddenForSize,
                            )
                        },
                    )
                }

                if (isExtrasShown && !isSinglePane) {
                    Spacer(Modifier.width(TacitSpacing.paneGap))
                }

                if (isExtrasShown) {
                    ModernPane(
                        modifier = Modifier.weight(if (isSinglePane) 1F else SETTINGS_WEIGHT),
                        content = {
                            ThemedSurface(
                                style = MaterialTheme.components.details,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                ExtrasPaneContentSwitch(roomViewModel.extrasStack, isSinglePane)
                            }
                        },
                    )
                }

                if (showMembersPane) {
                    Spacer(Modifier.width(TacitSpacing.paneGap))
                    ModernPane(
                        modifier = Modifier.width(membersPaneWidth),
                        content = {
                            ChannelMembersPane(
                                roomMembers = roomMembers,
                                onMemberClick = { member, onNeedsConfirmation ->
                                    scope.launch {
                                        val existingRoomId =
                                            tacitTimelineViewModel.findExistingDirectMessageRoom(member.userId)
                                        if (existingRoomId != null) {
                                            TacitRoomNavigationState.openRoomForRoomId = existingRoomId.full
                                        } else {
                                            onNeedsConfirmation()
                                        }
                                    }
                                },
                                onConfirmStartDirectMessage = { member ->
                                    scope.launch {
                                        tacitTimelineViewModel.findOrCreateDirectMessageRoom(member.userId)
                                            ?.let { roomId ->
                                                TacitRoomNavigationState.openRoomForRoomId = roomId.full
                                            }
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}


@Composable
private fun TimelinePane(
    roomViewModel: RoomViewModel,
    showSettingsButton: Boolean,
    showBackButton: Boolean,
) {
    val i18n = DI.get<TacitI18nView>()
    val activeTimeline = roomViewModel.timelineStack.subscribeAsState().value.active.instance
    if (activeTimeline is TimelineRouter.Wrapper.None) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tacitSurface)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = i18n.tacitSelectChannelHint(),
                color = tacitTextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        RoomContentSwitch(
            roomViewModel.timelineStack,
            showSettingsButton,
            showBackButton,
        )
    }
}

@Composable
private fun ModernPane(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    TacitPaneSurface(
        modifier = modifier.fillMaxHeight(),
        shape = TacitShapes.pane,
        innerShape = TacitShapes.paneInner,
    ) { content() }
}
