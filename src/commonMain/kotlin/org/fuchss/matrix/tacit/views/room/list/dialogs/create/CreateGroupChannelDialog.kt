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
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*

@Composable
internal fun CreateGroupChannelDialog(
    isCreating: Boolean,
    canCreateGroupChannel: Boolean,
    onDismiss: () -> Unit,
    onCreateGroupChannel: (name: String, topic: String) -> Unit,
) {
    var roomName by remember { mutableStateOf("") }
    var roomTopic by remember { mutableStateOf("") }

    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text("Create Group Chat")
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Create an encrypted group chat outside your guilds.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Room name") },
                    prefix = { Text("#") },
                    maxLines = 1,
                )
                OutlinedTextField(
                    value = roomTopic,
                    onValueChange = { roomTopic = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Room topic (optional)") },
                    maxLines = 2,
                )
                if (!canCreateGroupChannel) {
                    Text(
                        "No active Matrix account available.",
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
                Text("Cancel")
            }
            ThemedButton(
                style = MaterialTheme.components.primaryButton,
                onClick = { onCreateGroupChannel(roomName, roomTopic) },
                enabled = !isCreating && canCreateGroupChannel && roomName.isNotBlank(),
            ) {
                Text(if (isCreating) "Creating..." else "Create")
            }
        }
    }
}
