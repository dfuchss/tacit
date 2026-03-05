package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

@Composable
internal fun CreateGroupChannelDialog(
    isCreating: Boolean,
    canCreateGroupChannel: Boolean,
    onDismiss: () -> Unit,
    onCreateGroupChannel: (name: String, topic: String) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    var roomName by remember { mutableStateOf("") }
    var roomTopic by remember { mutableStateOf("") }

    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text(i18n.tacitCreateGroupChatTitle())
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    i18n.tacitCreateGroupChatDescription(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(i18n.tacitRoomNameLabel()) },
                    prefix = { Text("#") },
                    maxLines = 1,
                )
                OutlinedTextField(
                    value = roomTopic,
                    onValueChange = { roomTopic = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(i18n.tacitRoomTopicOptionalLabel()) },
                    maxLines = 2,
                )
                if (!canCreateGroupChannel) {
                    Text(
                        i18n.tacitNoActiveAccount(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        ModalDialogFooter {
            ThemedButton(
                style = MaterialTheme.components.commonButton,
                onClick = onDismiss,
                enabled = !isCreating,
            ) {
                Text(i18n.commonCancel())
            }
            ThemedButton(
                style = MaterialTheme.components.primaryButton,
                onClick = { onCreateGroupChannel(roomName, roomTopic) },
                enabled = !isCreating && canCreateGroupChannel && roomName.isNotBlank(),
            ) {
                Text(if (isCreating) i18n.tacitCreateInProgress() else i18n.commonCreate())
            }
        }
    }
}
