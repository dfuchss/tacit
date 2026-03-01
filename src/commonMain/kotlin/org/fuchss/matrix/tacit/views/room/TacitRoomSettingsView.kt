package org.fuchss.matrix.tacit.views.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModel
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.viewmodel.room.settings.TacitDmVerificationEntry
import org.fuchss.matrix.tacit.viewmodel.room.settings.TacitDmVerificationStatus
import org.fuchss.matrix.tacit.viewmodel.room.settings.TacitRoomSettingsViewModel

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
            roomSettingsViewModel = roomSettingsViewModel as? TacitRoomSettingsViewModel
                ?: error("TacitRoomSettingsView requires RoomSettingsViewModelFactory to provide TacitRoomSettingsViewModel."),
            onClose = closeSettings,
            isSinglePane = if (isSpaceSettingsOpen) false else isSinglePane,
        )
    }
}

@Composable
private fun DmRoomSettings(
    roomSettingsViewModel: TacitRoomSettingsViewModel,
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

            DmUserVerificationSection(
                entries = roomSettingsViewModel.dmVerificationEntries.collectAsState().value,
                onOpenVerification = roomSettingsViewModel::openDmVerification,
            )

            HorizontalDivider(color = tacitBorder)

            RoomSettingsLeaveRoom(roomSettingsViewModel)

            if (leaveWarningOpen) {
                RoomSettingsLeaveRoomWarning(roomSettingsViewModel)
            }
        }
    }
}

@Composable
private fun DmUserVerificationSection(
    entries: List<TacitDmVerificationEntry>,
    onOpenVerification: (de.connect2x.trixnity.core.model.UserId) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "User Verification",
            style = MaterialTheme.typography.titleMedium,
            color = tacitText,
        )
        Text(
            text = "Verify your DM contact to confirm identity and device trust.",
            style = MaterialTheme.typography.bodySmall,
            color = tacitTextMuted,
        )

        if (entries.isEmpty()) {
            Text(
                text = "No DM contact found for verification.",
                style = MaterialTheme.typography.bodySmall,
                color = tacitTextMuted,
            )
            return@Column
        }

        entries.forEach { entry ->
            val statusText = entry.status.toDmVerificationLabel()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tacitPanelHigh, RoundedCornerShape(12.dp))
                    .border(1.dp, tacitBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemedUserAvatar(
                    initials = entry.initials,
                    image = entry.image,
                    size = 30.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tacitText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = tacitTextMuted,
                    )
                }
                Button(
                    onClick = { onOpenVerification(entry.userId) },
                    colors = ButtonDefaults.buttonColors(containerColor = tacitActionPrimaryBackground),
                ) {
                    Text(if (entry.status == TacitDmVerificationStatus.VERIFIED) "Open" else "Verify")
                }
            }
        }
    }
}

private fun TacitDmVerificationStatus.toDmVerificationLabel(): String = when (this) {
    TacitDmVerificationStatus.VERIFIED -> "Verified"
    TacitDmVerificationStatus.NEEDS_VERIFICATION -> "Not verified"
    TacitDmVerificationStatus.DEVICES_UNVERIFIED -> "Some devices unverified"
    TacitDmVerificationStatus.INVALID -> "Invalid verification state"
    TacitDmVerificationStatus.BLOCKED -> "Blocked"
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
