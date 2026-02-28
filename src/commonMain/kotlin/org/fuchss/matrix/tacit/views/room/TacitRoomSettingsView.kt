package org.fuchss.matrix.tacit.views.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.core.model.events.m.room.JoinRulesEventContent
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.VerticalScrollbar
import de.connect2x.trixnity.messenger.compose.view.common.ErrorView
import de.connect2x.trixnity.messenger.compose.view.common.HeaderBackButtonType
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.room.settings.*
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModel
import org.fuchss.matrix.tacit.TacitRoomNavigationState
import org.fuchss.matrix.tacit.tacitActionPrimaryBackground
import org.fuchss.matrix.tacit.tacitPanel
import org.fuchss.matrix.tacit.tacitPanelHigh
import org.fuchss.matrix.tacit.tacitBorder
import org.fuchss.matrix.tacit.tacitText
import org.fuchss.matrix.tacit.tacitTextMuted
import org.fuchss.matrix.tacit.tacitWarningBannerBg
import org.fuchss.matrix.tacit.tacitWarningBannerBorder

class TacitRoomSettingsView(
    private val delegate: RoomSettingsView = RoomSettingsViewImpl(),
) : RoomSettingsView {
    @Composable
    override fun create(roomSettingsViewModel: RoomSettingsViewModel, isSinglePane: Boolean) {
        val isDirect = roomSettingsViewModel.isDirect.collectAsState().value
        val isLeave = roomSettingsViewModel.isLeave.collectAsState().value
        val roomId = roomSettingsViewModel.roomId.full
        val isSpaceSettingsOpen = TacitRoomNavigationState.suppressBackButtonForRoomId == roomId
        val closeSettings = {
            if (isSpaceSettingsOpen) {
                TacitRoomNavigationState.closeRoomAfterSettingsForRoomId = roomId
            }
            roomSettingsViewModel.close()
        }

        LaunchedEffect(isLeave) {
            if (isLeave) {
                TacitRoomNavigationState.closeRoomAfterSettingsForRoomId = roomId
                closeSettings()
            }
        }

        if (isSpaceSettingsOpen && !isDirect) {
            GuildSettings(
                roomSettingsViewModel = roomSettingsViewModel,
            )
            return
        }

        if (!isDirect) {
            delegate.create(roomSettingsViewModel, isSinglePane)
            return
        }

        DmRoomSettings(
            roomSettingsViewModel = roomSettingsViewModel,
            onClose = closeSettings,
            isSinglePane = if (isSpaceSettingsOpen) false else isSinglePane,
        )
    }
}

@Composable
private fun DmRoomSettings(
    roomSettingsViewModel: RoomSettingsViewModel,
    onClose: () -> Unit,
    isSinglePane: Boolean,
) {
    val error = roomSettingsViewModel.error.collectAsState().value
    val leaveWarningOpen = roomSettingsViewModel.leaveRoomWarningOpen.collectAsState().value
    val scrollState = rememberScrollState()

    ExtrasPaneHeader(
        "Direct Message Settings",
        error,
        onClose,
        if (isSinglePane) HeaderBackButtonType.BACK else HeaderBackButtonType.CLOSE,
        null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Direct message rooms use shared account privacy. Room-wide settings are disabled here.",
                style = MaterialTheme.typography.bodySmall,
                color = tacitTextMuted,
            )

            RoomSettingsNotifications(roomSettingsViewModel.roomSettingsNotificationsViewModel)

            RoomSettingsMemberList(roomSettingsViewModel)

            HorizontalDivider(color = tacitBorder)

            RoomSettingsLeaveRoom(roomSettingsViewModel)

            if (leaveWarningOpen) {
                RoomSettingsLeaveRoomWarning(roomSettingsViewModel)
            }
        }
    }
}

@Composable
private fun GuildSettings(
    roomSettingsViewModel: RoomSettingsViewModel,
) {
    val i18n = DI.get<I18nView>()
    val error = roomSettingsViewModel.error.collectAsState().value
    val leaveWarningOpen = roomSettingsViewModel.leaveRoomWarningOpen.collectAsState().value
    val joinRule = roomSettingsViewModel.roomSettingsJoinRulesViewModel.joinRule.collectAsState().value
    val scroll = rememberScrollState()
    val warningAccent = tacitWarningBannerBorder
    val warningContainer = tacitWarningBannerBg

    Box(
        Modifier
            .fillMaxSize()
            .background(tacitPanel)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .background(
                        color = warningContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = warningAccent.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Guild Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = tacitText,
                )
                Spacer(Modifier.weight(1f))
                Tooltip(i18n.devInfoButtonTooltip()) {
                    IconButton(onClick = { roomSettingsViewModel.openDevInfoView() }) {
                        Icon(
                            Icons.Default.Info,
                            i18n.devInfoButtonTooltip(),
                            tint = warningAccent,
                        )
                    }
                }
            }
            HorizontalDivider(color = warningAccent.copy(alpha = 0.45f))
            if (error != null) {
                ErrorView(error)
            }
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .verticalScroll(scroll)
                        .background(tacitPanelHigh.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(PaddingValues(vertical = 0.dp, horizontal = 20.dp)),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ChangeRoomAvatar(roomSettingsViewModel.changeRoomAvatarViewModel)
                    RoomSettingsName(roomSettingsViewModel.roomSettingsNameViewModel)
                    RoomSettingsTopic(roomSettingsViewModel.roomSettingsTopicViewModel)

                    val roomAliasViewModel = roomSettingsViewModel.roomSettingsAliasViewModel
                    val showRoomAliasSettings = roomAliasViewModel.showRoomAliasSettings.collectAsState().value
                    if (showRoomAliasSettings) RoomSettingsAlias(roomAliasViewModel)

                    if (joinRule == JoinRulesEventContent.JoinRule.Public) {
                        HorizontalDivider(color = tacitBorder)
                        RoomSettingsSecurity(roomSettingsViewModel.roomSettingsSecurityViewModel)
                    }

                    HorizontalDivider(color = tacitBorder)
                    RoomSettingsJoinRules(roomSettingsViewModel)
                    HorizontalDivider(color = tacitBorder)
                    RoomSettingsMemberList(roomSettingsViewModel)
                    HorizontalDivider(color = tacitBorder)
                    RoomSettingsPowerlevel(roomSettingsViewModel)
                    RoomSettingsExportRoom(roomSettingsViewModel)
                    RoomSettingsLeaveRoom(roomSettingsViewModel)
                    if (leaveWarningOpen) RoomSettingsLeaveRoomWarning(roomSettingsViewModel)
                }
                VerticalScrollbar(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 2.dp),
                    scroll,
                )
            }
        }
    }
}
