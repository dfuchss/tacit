package org.fuchss.matrix.tacit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal object TacitRoomNavigationState {
    var openRoomForRoomId: String? by mutableStateOf(null)
    var openSettingsForRoomId: String? by mutableStateOf(null)
    var suppressBackButtonForRoomId: String? by mutableStateOf(null)
    var closeRoomAfterSettingsForRoomId: String? by mutableStateOf(null)
    var showMembersPane: Boolean by mutableStateOf(false)
}
