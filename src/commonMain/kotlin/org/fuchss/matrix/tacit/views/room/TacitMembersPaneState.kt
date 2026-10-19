package org.fuchss.matrix.tacit.views.room

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the channel members pane is shown next to the timeline.
 *
 * Plain UI state shared by the room header (toggle), the room pane (renders it) and the room list (hides it when a DM
 * is opened). It deliberately outlives a single room so the pane stays open while switching channels, hence it is
 * hoisted to [org.fuchss.matrix.tacit.views.TacitMessengerView], the common parent of room list and room.
 */
@Stable
internal class TacitMembersPaneState {
    var visible: Boolean by mutableStateOf(false)
        private set

    fun toggle() {
        visible = !visible
    }

    fun hide() {
        visible = false
    }
}

internal val LocalTacitMembersPane = staticCompositionLocalOf<TacitMembersPaneState> {
    error("TacitMembersPaneState is provided by TacitMessengerView.")
}
