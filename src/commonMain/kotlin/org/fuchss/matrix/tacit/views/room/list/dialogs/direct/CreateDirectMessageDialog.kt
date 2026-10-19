package org.fuchss.matrix.tacit.views.room.list.dialogs.direct

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
internal fun CreateDirectMessageDialog(
    isStartingDirectMessage: Boolean,
    canStartDirectMessage: Boolean,
    canSearchUsers: Boolean,
    userSearch: UserSearchState,
    onSearchUsers: (query: String) -> Unit,
    presetUserId: String? = null,
    presetDisplayName: String? = null,
    onDismiss: () -> Unit,
    onStartDirectMessage: (userId: String) -> Unit,
    submitError: String? = null,
) {
    val i18n = DI.get<TacitI18nView>()
    val hasPresetUser = !presetUserId.isNullOrBlank()
    var userId by remember(presetUserId) { mutableStateOf(presetUserId.orEmpty()) }
    TacitUserSearchEffect(
        query = userId,
        enabled = !hasPresetUser && canStartDirectMessage && canSearchUsers,
        onSearch = onSearchUsers,
    )

    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text(if (hasPresetUser) i18n.tacitStartChat() else i18n.tacitStartDirectMessage())
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasPresetUser) {
                    val targetDisplayName = presetDisplayName?.ifBlank { null } ?: userId
                    TacitDialogHeroCard(title = i18n.tacitOpenDirectMessageWith(targetDisplayName))
                } else {
                    TacitDialogHeroCard(title = i18n.tacitSearchUserOrEnterMatrixId())
                }
                OutlinedTextField(
                    value = userId,
                    onValueChange = { if (!hasPresetUser) userId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(i18n.tacitMatrixUserIdLabel()) },
                    placeholder = { Text(i18n.tacitUserIdPlaceholder()) },
                    maxLines = 1,
                    enabled = !hasPresetUser,
                )
                if (!hasPresetUser && canSearchUsers && userId.trim().length >= 2) {
                    TacitUserSearchResults(state = userSearch, selectedUserId = userId, onSelect = { userId = it })
                }
                if (!canStartDirectMessage) {
                    Text(
                        i18n.tacitNoActiveAccount(),
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
                enabled = !isStartingDirectMessage,
            ) {
                Text(i18n.commonCancel())
            }
            ThemedButton(
                style = MaterialTheme.components.primaryButton,
                onClick = { onStartDirectMessage(userId) },
                enabled = !isStartingDirectMessage && canStartDirectMessage && userId.isNotBlank(),
            ) {
                Text(if (isStartingDirectMessage) i18n.tacitStartingInProgress() else i18n.tacitStartChat())
            }
        }
    }
}
