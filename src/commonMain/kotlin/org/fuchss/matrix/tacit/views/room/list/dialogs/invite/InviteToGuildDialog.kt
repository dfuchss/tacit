package org.fuchss.matrix.tacit.views.room.list.dialogs.invite

import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import org.fuchss.matrix.tacit.tacitTextMuted
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitDialogHeroCard
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitDialogSearchResultsContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitUserDirectoryRow

@Composable
internal fun InviteToGuildDialog(
    guildName: String,
    isInviting: Boolean,
    canInviteMember: Boolean,
    canSearchUsers: Boolean,
    searchUsers: suspend (query: String) -> Result<List<UserDirectoryEntry>>,
    onDismiss: () -> Unit,
    onInviteMember: (userId: String, reason: String) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    var userId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var searchInProgress by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<UserDirectoryEntry>>(emptyList()) }

    LaunchedEffect(userId, canInviteMember, canSearchUsers) {
        val query = userId.trim()
        if (!canInviteMember || !canSearchUsers || query.length < 2) {
            searchInProgress = false
            searchError = null
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(220)
        searchInProgress = true
        searchError = null
        searchUsers(query).fold(
            onSuccess = { entries ->
                searchResults = entries
            },
            onFailure = { throwable ->
                searchResults = emptyList()
                searchError = throwable.message ?: i18n.tacitSearchFailed()
            }
        )
        searchInProgress = false
    }

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
                    when {
                        searchInProgress -> {
                            Text(
                                i18n.tacitSearchingUsers(),
                                style = MaterialTheme.typography.bodySmall,
                                color = tacitTextMuted,
                            )
                        }

                        searchResults.isNotEmpty() -> {
                            TacitDialogSearchResultsContainer {
                                searchResults.forEach { user ->
                                    TacitUserDirectoryRow(
                                        user = user,
                                        selected = userId == user.userId,
                                        selectedLabel = i18n.actionOk(),
                                        onClick = { userId = user.userId },
                                    )
                                }
                            }
                        }

                        searchError != null -> {
                            Text(
                                searchError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
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
