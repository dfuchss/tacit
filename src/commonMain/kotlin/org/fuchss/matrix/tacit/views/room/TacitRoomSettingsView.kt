package org.fuchss.matrix.tacit.views.room

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import de.connect2x.trixnity.messenger.compose.view.room.settings.*
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModel
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.viewmodel.room.settings.TacitDmVerificationEntry
import org.fuchss.matrix.tacit.viewmodel.room.settings.TacitDmVerificationStatus
import org.fuchss.matrix.tacit.viewmodel.room.settings.TacitRoomSettingsViewModel
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

class TacitRoomSettingsView(
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
            GroupRoomSettings(
                roomSettingsViewModel = roomSettingsViewModel,
                isSinglePane = isSinglePane,
            )
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
    val i18n = DI.get<TacitI18nView>()
    val error = roomSettingsViewModel.error.collectAsState().value
    val leaveWarningOpen = roomSettingsViewModel.leaveRoomWarningOpen.collectAsState().value
    val scrollState = rememberScrollState()

    ExtrasPaneHeader(
        i18n.tacitDirectMessageSettingsTitle(),
        error,
        onClose,
        if (isSinglePane) HeaderBackButtonType.BACK else HeaderBackButtonType.CLOSE,
        null,
    ) {
        SettingsScrollableContent(
            scrollState = scrollState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
            verticalSpacing = 12.dp,
            showScrollbar = false,
        ) {
            Text(
                text = i18n.tacitDirectMessageSettingsInfo(),
                style = MaterialTheme.typography.bodySmall,
                color = tacitTextMuted,
            )

            RoomSettingsNotifications(roomSettingsViewModel.roomSettingsNotificationsViewModel)

            DmUserVerificationSection(
                entries = roomSettingsViewModel.dmVerificationEntries.collectAsState().value,
                onOpenVerification = roomSettingsViewModel::openDmVerification,
            )

            HorizontalDivider(color = tacitBorder)
            LeaveRoomSections(
                roomSettingsViewModel = roomSettingsViewModel,
                leaveWarningOpen = leaveWarningOpen,
            )
        }
    }
}

@Composable
private fun SettingsScrollableContent(
    scrollState: androidx.compose.foundation.ScrollState,
    contentPadding: PaddingValues,
    verticalSpacing: androidx.compose.ui.unit.Dp,
    showScrollbar: Boolean,
    columnModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(scrollState)
                .then(columnModifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            content()
        }
        if (showScrollbar) {
            VerticalScrollbar(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 2.dp),
                scrollState,
            )
        }
    }
}

@Composable
private fun RoomIdentityAndSecuritySections(
    roomSettingsViewModel: RoomSettingsViewModel,
    joinRule: JoinRulesEventContent.JoinRule,
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
}

@Composable
private fun ColumnScope.CommonRoomSections(
    roomSettingsViewModel: RoomSettingsViewModel,
    joinRule: JoinRulesEventContent.JoinRule,
    leaveWarningOpen: Boolean,
    includeNotifications: Boolean,
    includeHistoryVisibility: Boolean,
) {
    RoomIdentityAndSecuritySections(
        roomSettingsViewModel = roomSettingsViewModel,
        joinRule = joinRule,
    )

    if (includeNotifications) {
        HorizontalDivider(color = tacitBorder)
        RoomSettingsNotifications(roomSettingsViewModel.roomSettingsNotificationsViewModel)
    }
    if (includeHistoryVisibility) {
        HorizontalDivider(color = tacitBorder)
        RoomSettingsHistoryVisibility(roomSettingsViewModel)
    }

    HorizontalDivider(color = tacitBorder)
    RoomSettingsJoinRules(roomSettingsViewModel)
    HorizontalDivider(color = tacitBorder)
    RoomSettingsMemberList(roomSettingsViewModel)
    HorizontalDivider(color = tacitBorder)
    RoomSettingsPowerlevel(roomSettingsViewModel)
    RoomSettingsExportRoom(roomSettingsViewModel)
    LeaveRoomSections(
        roomSettingsViewModel = roomSettingsViewModel,
        leaveWarningOpen = leaveWarningOpen,
    )
}

@Composable
private fun LeaveRoomSections(
    roomSettingsViewModel: RoomSettingsViewModel,
    leaveWarningOpen: Boolean,
) {
    RoomSettingsLeaveRoom(roomSettingsViewModel)
    if (leaveWarningOpen) {
        RoomSettingsLeaveRoomWarning(roomSettingsViewModel)
    }
}

@Composable
private fun DefaultSettingsDevInfoAction(
    i18n: TacitI18nView,
    roomSettingsViewModel: RoomSettingsViewModel,
) {
    Tooltip(i18n.devInfoButtonTooltip()) {
        IconButton(onClick = { roomSettingsViewModel.openDevInfoView() }) {
            Icon(Icons.Default.Info, i18n.devInfoButtonTooltip())
        }
    }
}

@Composable
private fun WarningSettingsDevInfoAction(
    i18n: TacitI18nView,
    roomSettingsViewModel: RoomSettingsViewModel,
    warningAccent: androidx.compose.ui.graphics.Color,
) {
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

@Composable
private fun GroupRoomSettingsContent(
    roomSettingsViewModel: RoomSettingsViewModel,
    joinRule: JoinRulesEventContent.JoinRule,
    leaveRoomWarningOpen: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
) {
    SettingsScrollableContent(
        scrollState = scroll,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 0.dp),
        verticalSpacing = 20.dp,
        showScrollbar = true,
    ) {
        CommonRoomSections(
            roomSettingsViewModel = roomSettingsViewModel,
            joinRule = joinRule,
            leaveWarningOpen = leaveRoomWarningOpen,
            includeNotifications = true,
            includeHistoryVisibility = true,
        )
    }
}

@Composable
private fun GuildSettingsContent(
    roomSettingsViewModel: RoomSettingsViewModel,
    joinRule: JoinRulesEventContent.JoinRule,
    leaveWarningOpen: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
) {
    SettingsScrollableContent(
        scrollState = scroll,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 0.dp),
        verticalSpacing = 20.dp,
        showScrollbar = true,
        columnModifier = Modifier.background(tacitPanelHigh.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
    ) {
        CommonRoomSections(
            roomSettingsViewModel = roomSettingsViewModel,
            joinRule = joinRule,
            leaveWarningOpen = leaveWarningOpen,
            includeNotifications = false,
            includeHistoryVisibility = false,
        )
    }
}

@Composable
private fun GroupRoomSettings(
    roomSettingsViewModel: RoomSettingsViewModel,
    isSinglePane: Boolean,
) {
    val i18n = DI.get<TacitI18nView>()
    val error = roomSettingsViewModel.error.collectAsState().value
    val leaveRoomWarningOpen = roomSettingsViewModel.leaveRoomWarningOpen.collectAsState().value
    val joinRule = roomSettingsViewModel.roomSettingsJoinRulesViewModel.joinRule.collectAsState().value
    val scroll = rememberScrollState()

    ExtrasPaneHeader(
        i18n.roomSettings(),
        error,
        { roomSettingsViewModel.close() },
        if (isSinglePane) HeaderBackButtonType.BACK else HeaderBackButtonType.CLOSE,
        {
            DefaultSettingsDevInfoAction(
                i18n = i18n,
                roomSettingsViewModel = roomSettingsViewModel,
            )
        }
    ) {
        GroupRoomSettingsContent(
            roomSettingsViewModel = roomSettingsViewModel,
            joinRule = joinRule,
            leaveRoomWarningOpen = leaveRoomWarningOpen,
            scroll = scroll,
        )
    }
}

@Composable
private fun GuildSettings(
    roomSettingsViewModel: RoomSettingsViewModel,
) {
    val i18n = DI.get<TacitI18nView>()
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
                    text = i18n.tacitGuildSettingsTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    color = tacitText,
                )
                Spacer(Modifier.weight(1f))
                WarningSettingsDevInfoAction(
                    i18n = i18n,
                    roomSettingsViewModel = roomSettingsViewModel,
                    warningAccent = warningAccent,
                )
            }
            HorizontalDivider(color = warningAccent.copy(alpha = 0.45f))
            if (error != null) {
                ErrorView(error)
            }
            GuildSettingsContent(
                roomSettingsViewModel = roomSettingsViewModel,
                joinRule = joinRule,
                leaveWarningOpen = leaveWarningOpen,
                scroll = scroll,
            )
        }
    }
}

@Composable
private fun DmUserVerificationSection(
    entries: List<TacitDmVerificationEntry>,
    onOpenVerification: (de.connect2x.trixnity.core.model.UserId) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = i18n.tacitUserProfileTitle(),
            style = MaterialTheme.typography.titleMedium,
            color = tacitText,
        )
        Text(
            text = i18n.tacitUserProfileDescription(),
            style = MaterialTheme.typography.bodySmall,
            color = tacitTextMuted,
        )

        if (entries.isEmpty()) {
            Text(
                text = i18n.tacitNoDmContactForProfile(),
                style = MaterialTheme.typography.bodySmall,
                color = tacitTextMuted,
            )
            return@Column
        }

        entries.forEach { entry ->
            val statusText = entry.status.toDmVerificationLabel(i18n)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tacitPanelHigh, RoundedCornerShape(12.dp))
                    .border(1.dp, tacitBorder, RoundedCornerShape(12.dp))
                    .clickable { onOpenVerification(entry.userId) }
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
            }
        }
    }
}

private fun TacitDmVerificationStatus.toDmVerificationLabel(i18n: TacitI18nView): String = when (this) {
    TacitDmVerificationStatus.VERIFIED -> i18n.tacitVerified()
    TacitDmVerificationStatus.NEEDS_VERIFICATION -> i18n.tacitNotVerified()
    TacitDmVerificationStatus.DEVICES_UNVERIFIED -> i18n.tacitSomeDevicesUnverified()
    TacitDmVerificationStatus.INVALID -> i18n.tacitInvalidVerificationState()
    TacitDmVerificationStatus.BLOCKED -> i18n.tacitBlocked()
}
