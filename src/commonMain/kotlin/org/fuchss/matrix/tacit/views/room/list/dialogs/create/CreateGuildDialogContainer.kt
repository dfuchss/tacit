package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import de.connect2x.trixnity.client.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.createGuild

@Composable
internal fun CreateGuildDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    preferredCreationClient: MatrixClient?,
    scope: CoroutineScope,
    onSetOpen: (Boolean) -> Unit,
    onSetInProgress: (Boolean) -> Unit,
    onSetError: (String?) -> Unit,
    onSetSelectedGuild: (GuildEntry?) -> Unit,
) {
    if (!open) return

    CreateGuildDialog(
        isCreating = inProgress,
        canCreateGuild = preferredCreationClient != null,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateGuild = { name, topic, addDefaultChannel ->
            if (preferredCreationClient == null) {
                onSetOpen(false)
                onSetError("No active Matrix account available for guild creation.")
                return@CreateGuildDialog
            }
            scope.launch {
                onSetInProgress(true)
                onSetError(null)
                preferredCreationClient.createGuild(
                    guildName = name,
                    guildTopic = topic,
                    createDefaultChannel = addDefaultChannel,
                ).fold(
                    onSuccess = { guild ->
                        onSetSelectedGuild(guild)
                        onSetOpen(false)
                    },
                    onFailure = { throwable ->
                        onSetOpen(false)
                        onSetError("Could not create guild: ${throwable.message ?: "Unknown error"}")
                    },
                )
                onSetInProgress(false)
            }
        },
    )
}
