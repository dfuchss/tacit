package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*

@Composable
internal fun CreateGuildDialog(
    isCreating: Boolean,
    canCreateGuild: Boolean,
    onDismiss: () -> Unit,
    onCreateGuild: (name: String, topic: String, createDefaultChannel: Boolean) -> Unit,
) {
    var guildName by remember { mutableStateOf("") }
    var guildTopic by remember { mutableStateOf("") }
    var createDefaultChannel by remember { mutableStateOf(true) }

    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text("Create Guild")
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Create a Matrix Space and use it like a Tacit guild.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = guildName,
                    onValueChange = { guildName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Guild name") },
                    maxLines = 1,
                )
                OutlinedTextField(
                    value = guildTopic,
                    onValueChange = { guildTopic = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Guild topic (optional)") },
                    maxLines = 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = createDefaultChannel,
                        onCheckedChange = { createDefaultChannel = it },
                    )
                    Text("Create #general room")
                }
                if (!canCreateGuild) {
                    Text(
                        "No account available. Select or add an account first.",
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
                onClick = {
                    onCreateGuild(guildName, guildTopic, createDefaultChannel)
                },
                enabled = !isCreating && canCreateGuild && guildName.isNotBlank(),
            ) {
                Text(if (isCreating) "Creating..." else "Create")
            }
        }
    }
}
