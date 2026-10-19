package org.fuchss.matrix.tacit.views.room.list

import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.collectAsTextFieldValueState
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.ui.TacitSpacing
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

@Composable
internal fun DmChannelToolbar(
    i18n: TacitI18nView,
    roomListViewModel: RoomListViewModel,
    canCreateRoom: Boolean,
    onCreateRoom: () -> Unit,
    onCreateGroupChannel: () -> Unit,
) {
    ChannelToolbarScaffold(
        roomListViewModel = roomListViewModel,
        i18n = i18n,
        searchPlaceholder = i18n.tacitSearchFriendsPlaceholder(),
        toolbarVerticalPadding = 7.dp,
        searchVerticalPadding = 7.dp,
        actionSpacing = 8.dp,
    ) {
        ToolbarIconButton(
            icon = Icons.Default.Add,
            contentDescription = i18n.tacitNewDmDescription(),
            onClick = onCreateRoom,
            enabled = canCreateRoom,
            primary = true,
        )
        ToolbarIconButton(
            icon = Icons.Default.GroupAdd,
            contentDescription = i18n.tacitNewGroupChatDescription(),
            onClick = onCreateGroupChannel,
            enabled = canCreateRoom,
        )
    }
}

@Composable
internal fun GuildChannelToolbar(
    i18n: TacitI18nView,
    roomListViewModel: RoomListViewModel,
    canCreateRoom: Boolean,
    onCreateRoom: () -> Unit,
    onCreateCategory: (() -> Unit)?,
    onBrowseChannels: (() -> Unit)?,
    onInviteToGuild: (() -> Unit)?,
    canInviteToGuild: Boolean,
    onOpenGuildSettings: (() -> Unit)?,
    canEditGuildSettings: Boolean,
) {
    ChannelToolbarScaffold(
        roomListViewModel = roomListViewModel,
        i18n = i18n,
        searchPlaceholder = i18n.tacitSearchRoomsPlaceholder(),
        toolbarVerticalPadding = 4.dp,
        searchVerticalPadding = 5.dp,
        actionSpacing = 6.dp,
    ) {
        ToolbarIconButton(
            icon = Icons.Default.Add,
            contentDescription = i18n.tacitNewChannelDescription(),
            onClick = onCreateRoom,
            enabled = canCreateRoom,
            primary = true,
        )
        if (onCreateCategory != null) {
            ToolbarIconButton(
                icon = Icons.Default.CreateNewFolder,
                contentDescription = i18n.tacitNewCategoryDescription(),
                onClick = onCreateCategory,
                enabled = canCreateRoom,
            )
        }
        if (onBrowseChannels != null) {
            ToolbarIconButton(
                icon = Icons.Default.Search,
                contentDescription = i18n.tacitBrowseRoomsDescription(),
                onClick = onBrowseChannels,
                enabled = true,
            )
        }
        if (onInviteToGuild != null) {
            ToolbarIconButton(
                icon = Icons.Default.PersonAdd,
                contentDescription = i18n.tacitInviteMembersDescription(),
                onClick = onInviteToGuild,
                enabled = canInviteToGuild,
            )
        }
        if (onOpenGuildSettings != null) {
            ToolbarIconButton(
                icon = Icons.Default.Settings,
                contentDescription = i18n.tacitGuildSettingsDescription(),
                onClick = onOpenGuildSettings,
                enabled = canEditGuildSettings,
            )
        }
    }
}

/** Search field plus action buttons shared by the DM and guild toolbars; only the spacing, placeholder and actions differ. */
@Composable
private fun ChannelToolbarScaffold(
    roomListViewModel: RoomListViewModel,
    i18n: TacitI18nView,
    searchPlaceholder: String,
    toolbarVerticalPadding: androidx.compose.ui.unit.Dp,
    searchVerticalPadding: androidx.compose.ui.unit.Dp,
    actionSpacing: androidx.compose.ui.unit.Dp,
    actions: @Composable RowScope.() -> Unit,
) {
    var searchText by roomListViewModel.searchTerm.collectAsTextFieldValueState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TacitSpacing.paneInsetPadding, vertical = toolbarVerticalPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarSearchField(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                placeholder = searchPlaceholder,
                searchVerticalPadding = searchVerticalPadding,
                clearSearchLabel = i18n.tacitClearSearchDescription(),
                onClear = { roomListViewModel.searchTerm.update("") },
            )
            ToolbarActionsRow(
                actionSpacing = actionSpacing,
                content = actions,
            )
        }
    }
}

@Composable
private fun RowScope.ToolbarSearchField(
    searchText: androidx.compose.ui.text.input.TextFieldValue,
    onSearchTextChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    placeholder: String,
    searchVerticalPadding: androidx.compose.ui.unit.Dp,
    clearSearchLabel: String,
    onClear: () -> Unit,
) {
    val searchHoverSource = remember { MutableInteractionSource() }
    val searchHovered by searchHoverSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 150.dp)
            .clip(TacitShapes.control)
            .background(if (searchHovered) MaterialTheme.tacit.surfaceAlt else MaterialTheme.tacit.surface)
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, TacitShapes.control)
            .hoverable(searchHoverSource)
            .padding(horizontal = 8.dp, vertical = searchVerticalPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.tacit.text),
            cursorBrush = SolidColor(MaterialTheme.tacit.accent),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchText.text.isBlank()) {
                            Text(
                                placeholder,
                                color = MaterialTheme.tacit.textMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        innerTextField()
                    }
                    if (searchText.text.isNotBlank()) {
                        val clearInteractionSource = remember { MutableInteractionSource() }
                        Icon(
                            Icons.Default.Close,
                            contentDescription = clearSearchLabel,
                            tint = MaterialTheme.tacit.textMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(
                                    interactionSource = clearInteractionSource,
                                    indication = LocalIndication.current,
                                    onClickLabel = clearSearchLabel,
                                    role = Role.Button,
                                    onClick = onClear,
                                )
                                .tacitInteractive(
                                    interactionSource = clearInteractionSource,
                                    shape = TacitShapes.circle,
                                ),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ToolbarActionsRow(
    actionSpacing: androidx.compose.ui.unit.Dp,
    content: @Composable RowScope.() -> Unit,
) {
    Spacer(Modifier.width(TacitSpacing.paneInsetPadding))
    Row(
        horizontalArrangement = Arrangement.spacedBy(actionSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean = false,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val background = when {
        !enabled -> MaterialTheme.tacit.surface
        primary && hovered -> MaterialTheme.tacit.accent
        primary -> MaterialTheme.tacit.accent(TacitAlpha.accentFill)
        hovered -> MaterialTheme.tacit.surfaceAlt
        else -> MaterialTheme.tacit.surface
    }
    val borderColor = when {
        !enabled -> MaterialTheme.tacit.border
        primary -> MaterialTheme.tacit.accent
        else -> MaterialTheme.tacit.border
    }
    val iconTint = when {
        !enabled -> MaterialTheme.tacit.textMuted
        else -> MaterialTheme.tacit.text
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(TacitShapes.control)
            .background(background)
            .border(TacitSizes.borderWidth, borderColor, TacitShapes.control)
            .hoverable(hoverSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onClick,
            )
            .tacitInteractive(
                interactionSource = interactionSource,
                shape = TacitShapes.control,
                enabled = enabled,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
    }
}
