package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.collectAsTextFieldValueState
import de.connect2x.trixnity.messenger.compose.view.pointerMoveFilter
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

@Composable
internal fun DmChannelToolbar(
    i18n: TacitI18nView,
    roomListViewModel: RoomListViewModel,
    canCreateRoom: Boolean,
    onCreateRoom: () -> Unit,
    onCreateGroupChannel: () -> Unit,
) {
    var searchText by roomListViewModel.searchTerm.collectAsTextFieldValueState()
    val toolbarVerticalPadding = 7.dp
    val searchVerticalPadding = 7.dp
    val actionSpacing = 8.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tacitSurface)
            .padding(horizontal = 8.dp, vertical = toolbarVerticalPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarSearchField(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                placeholder = i18n.tacitSearchFriendsPlaceholder(),
                searchVerticalPadding = searchVerticalPadding,
                clearSearchLabel = i18n.tacitClearSearchDescription(),
                onClear = { roomListViewModel.searchTerm.update("") },
            )
            ToolbarActionsRow(
                actionSpacing = actionSpacing,
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
    var searchText by roomListViewModel.searchTerm.collectAsTextFieldValueState()
    val toolbarVerticalPadding = 4.dp
    val searchVerticalPadding = 5.dp
    val actionSpacing = 6.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tacitSurface)
            .padding(horizontal = 8.dp, vertical = toolbarVerticalPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarSearchField(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                placeholder = i18n.tacitSearchRoomsPlaceholder(),
                searchVerticalPadding = searchVerticalPadding,
                clearSearchLabel = i18n.tacitClearSearchDescription(),
                onClear = { roomListViewModel.searchTerm.update("") },
            )
            ToolbarActionsRow(
                actionSpacing = actionSpacing,
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
    var searchHovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (searchHovered) tacitSurfaceAlt else tacitSurface)
            .pointerMoveFilter(
                onEnter = {
                    searchHovered = true
                    true
                },
                onExit = {
                    searchHovered = false
                    true
                },
            )
            .padding(horizontal = 8.dp, vertical = searchVerticalPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = tacitText),
            cursorBrush = SolidColor(accentColor),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchText.text.isBlank()) {
                            Text(
                                placeholder,
                                color = tacitTextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        innerTextField()
                    }
                    if (searchText.text.isNotBlank()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = clearSearchLabel,
                            tint = tacitTextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onClear() },
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
    Spacer(Modifier.width(8.dp))
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
    var hovered by remember { mutableStateOf(false) }
    val background = when {
        !enabled -> tacitSurface
        primary && hovered -> accentColor
        primary -> tacitAccent(0.25f)
        hovered -> tacitSurfaceAlt
        else -> tacitSurface
    }
    val borderColor = when {
        !enabled -> tacitBorder
        primary -> accentColor
        else -> tacitBorder
    }
    val iconTint = when {
        !enabled -> tacitTextMuted
        else -> tacitText
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .pointerMoveFilter(
                onEnter = {
                    if (enabled) hovered = true
                    true
                },
                onExit = {
                    hovered = false
                    true
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(17.dp),
        )
    }
}
