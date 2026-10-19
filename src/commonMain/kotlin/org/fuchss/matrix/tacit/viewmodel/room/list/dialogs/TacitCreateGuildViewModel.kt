package org.fuchss.matrix.tacit.viewmodel.room.list.dialogs

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.createGuild

/** "Create Guild": creates a space (optionally with a #general room) with the account used for creating rooms. */
internal class TacitCreateGuildViewModel(
    viewModelContext: ViewModelContext,
    private val creationClient: () -> MatrixClient?,
    private val onGuildCreated: (GuildEntry) -> Unit,
) : TacitDialogViewModel(viewModelContext) {

    fun createGuild(name: String, topic: String, createDefaultChannel: Boolean, onSuccess: () -> Unit) {
        action.launch(TacitI18n::tacitCreateGuildFailed, onSuccess = { guild: GuildEntry ->
            onGuildCreated(guild)
            onSuccess()
        }) {
            creationClient()?.createGuild(guildName = name, guildTopic = topic, createDefaultChannel = createDefaultChannel)
                ?: Result.failure(IllegalStateException(tacitI18n.tacitNoActiveAccountForGuildCreation()))
        }
    }
}
