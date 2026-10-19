package org.fuchss.matrix.tacit.viewmodel.room.list.dialogs

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.createCategory

/** "Create Category": creates a child space of the guild. It shows up once a channel is moved into it. */
internal class TacitCreateCategoryViewModel(
    viewModelContext: ViewModelContext,
    private val guildClient: (GuildEntry) -> MatrixClient?,
) : TacitDialogViewModel(viewModelContext) {

    fun createCategory(guild: GuildEntry, name: String, onSuccess: () -> Unit) {
        action.launch(TacitI18n::tacitCreateCategoryFailed, onSuccess = { _: Any? -> onSuccess() }) {
            guildClient(guild)?.let { client -> guild.createCategory(matrixClient = client, categoryName = name) }
                ?: Result.failure(IllegalStateException(tacitI18n.tacitNoGuildContext()))
        }
    }
}
