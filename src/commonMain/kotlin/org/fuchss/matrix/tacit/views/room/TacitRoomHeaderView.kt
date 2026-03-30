package org.fuchss.matrix.tacit.views.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.pointerMoveFilter
import de.connect2x.trixnity.messenger.compose.view.room.timeline.RoomHeaderView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.RoomHeaderViewImpl
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.RoomHeaderViewModel
import org.fuchss.matrix.tacit.*
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
        Box(modifier = Modifier.fillMaxWidth()) {
            delegate.create(
                roomHeaderViewModel = roomHeaderViewModel,
                showSettingsButton = false,
                showBackButton = showBackButton,
            )
            if (showMembersButton) {
                MembersPaneToggleButton(
                    active = TacitRoomNavigationState.showMembersPane,
                    onClick = {
                        TacitRoomNavigationState.showMembersPane = !TacitRoomNavigationState.showMembersPane
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp),
                )
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
    var hovered by remember { mutableStateOf(false) }
    val background = when {
        active -> tacitAccent(0.2f)
        hovered -> tacitSurfaceAlt
        else -> tacitSurface
    }
    val border = when {
        active -> accentColor
        else -> tacitBorder
    }
    val iconColor = if (active) tacitText else tacitTextMuted

    Box(
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .pointerMoveFilter(
                onEnter = {
                    hovered = true
                    true
                },
                onExit = {
                    hovered = false
                    true
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = i18n.tacitToggleMembersPane(),
            tint = iconColor,
            modifier = Modifier.size(16.dp),
        )
    }
}
