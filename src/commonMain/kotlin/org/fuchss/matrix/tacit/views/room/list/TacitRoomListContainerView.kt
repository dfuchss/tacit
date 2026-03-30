package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.roomlist.RoomList
import de.connect2x.trixnity.messenger.compose.view.roomlist.RoomListContainerView
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.AccountData
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.NotVerifiedBanner
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.SearchRoomsBanner
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import de.connect2x.trixnity.messenger.viewmodel.util.ErrorType

class TacitRoomListContainerView : RoomListContainerView {
    @Composable
    override fun create(roomListViewModel: RoomListViewModel) {
        val i18n = DI.get<I18nView>()
        val error = roomListViewModel.error.collectAsState().value
        ThemedSurface(
            style = MaterialTheme.components.sidebar,
        ) {
            if (error != null) {
                ThemedModalDialog({ roomListViewModel.errorDismiss() }) {
                    ModalDialogHeader {
                        Text(i18n.anErrorHasOccurred())
                    }
                    ModalDialogContent {
                        Text(error)
                        if (roomListViewModel.errorType.value == ErrorType.WITH_ACTION) {
                            Text(i18n.roomListRemoveRoom(), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    ModalDialogFooter {
                        ThemedButton(
                            style = MaterialTheme.components.primaryButton,
                            onClick = { roomListViewModel.errorDismiss() },
                        ) {
                            Text(i18n.actionOk())
                        }
                    }
                }
            }
            Column {
                AccountData(roomListViewModel)
                HorizontalDivider(Modifier.fillMaxWidth().width(1.dp))
                NotVerifiedBanner(roomListViewModel)
                SearchRoomsBanner(roomListViewModel)
                RoomList(roomListViewModel)
            }
        }
    }
}
