package org.fuchss.matrix.tacit.views.room.list.dialogs.invite

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
import org.fuchss.matrix.tacit.viewmodel.util.UserSearchState
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitDialogHeroCard
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitDialogSubmitError
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitUserSearchEffect
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitUserSearchResults

@Composable
internal fun InviteToGuildDialog(
    guildName: String,
    isInviting: Boolean,
    canInviteMember: Boolean,
    canSearchUsers: Boolean,
    userSearch: UserSearchState,
    onSearchUsers: (query: String) -> Unit,
    onDismiss: () -> Unit,
    onInviteMember: (userId: String, reason: String) -> Unit,
    submitError: String? = null,
) {
    val i18n = DI.get<TacitI18nView>()
    var userId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    TacitUserSearchEffect(query = userId, enabled = canInviteMember && canSearchUsers, onSearch = onSearchUsers)

    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text(i18n.tacitInviteToGuildTitle())
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TacitDialogHeroCard(title = i18n.tacitInviteUserToGuild(guildName))
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(i18n.tacitMatrixUserIdLabel()) },
                    placeholder = { Text(i18n.tacitUserIdPlaceholder()) },
                    maxLines = 1,
                )
                if (canSearchUsers && userId.trim().length >= 2) {
                    TacitUserSearchResults(state = userSearch, selectedUserId = userId, onSelect = { userId = it })
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(i18n.tacitReasonOptionalLabel()) },
                    maxLines = 2,
                )
                if (!canInviteMember) {
                    Text(
                        i18n.tacitNoAccountForGuild(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TacitDialogSubmitError(submitError)
            }
        }
        ModalDialogFooter {
            ThemedButton(
                style = MaterialTheme.components.commonButton,
                onClick = onDismiss,
                enabled = !isInviting,
            ) {
                Text(i18n.commonCancel())
            }
            ThemedButton(
                style = MaterialTheme.components.primaryButton,
                onClick = { onInviteMember(userId, reason) },
                enabled = !isInviting && canInviteMember && userId.isNotBlank(),
            ) {
                Text(if (isInviting) i18n.tacitInvitingInProgress() else i18n.tacitSendInvite())
            }
        }
    }
}
