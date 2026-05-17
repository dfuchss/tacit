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
import kotlinx.coroutines.delay
import org.fuchss.matrix.tacit.tacitTextMuted
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitDialogHeroCard
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitDialogSearchResultsContainer
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitUserDirectoryRow

@Composable
internal fun CreateDirectMessageDialog(
    isStartingDirectMessage: Boolean,
    canStartDirectMessage: Boolean,
    canSearchUsers: Boolean,
    searchUsers: suspend (query: String) -> Result<List<UserDirectoryEntry>>,
    presetUserId: String? = null,
    presetDisplayName: String? = null,
    onDismiss: () -> Unit,
    onStartDirectMessage: (userId: String) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    val hasPresetUser = !presetUserId.isNullOrBlank()
    var userId by remember(presetUserId) { mutableStateOf(presetUserId.orEmpty()) }
    var searchInProgress by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<UserDirectoryEntry>>(emptyList()) }

    LaunchedEffect(userId, canStartDirectMessage, canSearchUsers, hasPresetUser) {
        val query = userId.trim()
        if (hasPresetUser || !canStartDirectMessage || !canSearchUsers || query.length < 2) {
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
                if (!canStartDirectMessage) {
                    Text(
                        i18n.tacitNoActiveAccount(),
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
