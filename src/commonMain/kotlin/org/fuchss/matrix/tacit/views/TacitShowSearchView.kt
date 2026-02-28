package org.fuchss.matrix.tacit.views

import androidx.compose.runtime.Composable
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.ShowSearchView
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel

class TacitShowSearchView : ShowSearchView {
    // Intentionally empty: Tacit has its own search.
    @Composable
    override fun create(roomListViewModel: RoomListViewModel) = Unit
}
