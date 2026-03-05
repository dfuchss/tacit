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
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

@Composable
internal fun CreateGuildDialog(
    isCreating: Boolean,
    canCreateGuild: Boolean,
    onDismiss: () -> Unit,
    onCreateGuild: (name: String, topic: String, createDefaultChannel: Boolean) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    var guildName by remember { mutableStateOf("") }
    var guildTopic by remember { mutableStateOf("") }
    var createDefaultChannel by remember { mutableStateOf(true) }

    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text(i18n.tacitCreateGuildTitle())
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    i18n.tacitCreateGuildDescription(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = guildName,
                    onValueChange = { guildName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(i18n.tacitGuildNameLabel()) },
                    maxLines = 1,
                )
                OutlinedTextField(
                    value = guildTopic,
                    onValueChange = { guildTopic = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(i18n.tacitGuildTopicOptionalLabel()) },
                    maxLines = 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = createDefaultChannel,
                        onCheckedChange = { createDefaultChannel = it },
                    )
                    Text(i18n.tacitCreateGeneralRoom())
                }
                if (!canCreateGuild) {
                    Text(
                        i18n.tacitNoAccountSelectOrAdd(),
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
                onClick = {
                    onCreateGuild(guildName, guildTopic, createDefaultChannel)
                },
                enabled = !isCreating && canCreateGuild && guildName.isNotBlank(),
            ) {
                Text(if (isCreating) i18n.tacitCreateInProgress() else i18n.commonCreate())
            }
        }
    }
}
