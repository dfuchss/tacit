package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.MatrixMessengerConfiguration
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.AccountOptionsView
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedIconButton
import de.connect2x.trixnity.messenger.viewmodel.roomlist.AccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel

class TacitAccountOptionsView : AccountOptionsView {
    @Composable
    override fun create(accountViewModel: AccountViewModel, roomListViewModel: RoomListViewModel) {
        val i18n = DI.get<I18nView>()
        val appName = DI.get<MatrixMessengerConfiguration>().appName
        val settingsLabel = i18n.commonSettings().capitalize(Locale.current)
        val aboutLabel = i18n.accountAboutTheApp(appName)

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tooltip(tooltip = { Text(settingsLabel) }) {
                ThemedIconButton(
                    style = MaterialTheme.components.commonIconButton,
                    onClick = accountViewModel::openUserSettings,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = settingsLabel,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Tooltip(tooltip = { Text(aboutLabel) }) {
                ThemedIconButton(
                    style = MaterialTheme.components.commonIconButton,
                    onClick = accountViewModel::openAppInfo,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = aboutLabel,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
