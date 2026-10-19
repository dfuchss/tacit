package org.fuchss.matrix.tacit.views.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.timeline.RoomHeaderView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.RoomHeaderViewImpl
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.RoomHeaderViewModel
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

class TacitRoomHeaderView(
    private val delegate: RoomHeaderView = RoomHeaderViewImpl(),
) : RoomHeaderView {
    @Composable
    override fun create(
        roomHeaderViewModel: RoomHeaderViewModel,
        showSettingsButton: Boolean,
        showBackButton: Boolean,
    ) {
        val isDirectChat = roomHeaderViewModel.isDirectChat.collectAsState().value
        val showMembersButton = !isDirectChat && !showBackButton
        // The delegate draws the header bar only over its own (weighted) width, so paint an identical
        // surface behind the whole row to let the bar span under the members button as well.
        val headerStyle = MaterialTheme.components.header
        val parentElevation = LocalAbsoluteTonalElevation.current
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = headerStyle.shape,
            color = headerStyle.color,
            contentColor = headerStyle.contentColor,
            tonalElevation = headerStyle.tonalElevation,
            shadowElevation = headerStyle.shadowElevation,
        ) {
            // Surface raised the elevation for its content; reset it so the delegate's own surface matches.
            CompositionLocalProvider(LocalAbsoluteTonalElevation provides parentElevation) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        delegate.create(
                            roomHeaderViewModel = roomHeaderViewModel,
                            showSettingsButton = false,
                            showBackButton = showBackButton,
                        )
                    }
                    if (showMembersButton) {
                        MembersPaneToggleButton(
                            active = TacitRoomNavigationState.showMembersPane,
                            onClick = {
                                TacitRoomNavigationState.showMembersPane = !TacitRoomNavigationState.showMembersPane
                            },
                            modifier = Modifier.padding(start = 8.dp, end = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersPaneToggleButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val i18n = DI.get<TacitI18nView>()
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val background = when {
        active -> MaterialTheme.tacit.accent(0.2f)
        hovered -> MaterialTheme.tacit.surfaceAlt
        else -> MaterialTheme.tacit.surface
    }
    val border = when {
        active -> MaterialTheme.tacit.accent
        else -> MaterialTheme.tacit.border
    }
    val iconColor = if (active) MaterialTheme.tacit.text else MaterialTheme.tacit.textMuted

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(TacitShapes.control)
            .background(background)
            .border(1.dp, border, TacitShapes.control)
            .hoverable(hoverSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = i18n.tacitToggleMembersPane(),
            tint = iconColor,
            modifier = Modifier.size(17.dp),
        )
    }
}
