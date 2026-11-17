package org.fuchss.matrix.tacit.viewmodel.room.list.dialogs

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import de.connect2x.trixnity.messenger.util.InviteUser
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.matrixUserDirectorySearch
import org.fuchss.matrix.tacit.viewmodel.util.inviteMember
import org.koin.core.component.get

/** "Invite to Guild": finds the user in the directory and invites them to the guild space. */
internal class TacitInviteToGuildViewModel(
    viewModelContext: ViewModelContext,
    private val guildClient: (GuildEntry) -> MatrixClient?,
) : TacitDialogViewModel(viewModelContext) {

    val userSearch = matrixUserDirectorySearch(coroutineScope, tacitI18n)

    fun searchUsers(guild: GuildEntry, query: String) = userSearch.search(query, guildClient(guild))

    fun inviteUser(guild: GuildEntry, userId: String, reason: String, onSuccess: () -> Unit) {
        action.launch(TacitI18n::tacitSendInviteFailed, onSuccess = { _: Any? -> onSuccess() }) {
            guildClient(guild)?.let { client ->
                val inviteUser = get<InviteUser>()
                guild.inviteMember(
                    invite = { roomId, invitee, inviteReason -> inviteUser(client, roomId, invitee, inviteReason) },
                    userId = userId,
                    reason = reason,
                )
            }
                ?: Result.failure(IllegalStateException(tacitI18n.tacitNoGuildContext()))
        }
    }
}
