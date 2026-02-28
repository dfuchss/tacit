package org.fuchss.matrix.tacit.views.room.list.dialogs.direct

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*
import kotlinx.coroutines.delay
import org.fuchss.matrix.tacit.tacitSearchResultBackground
import org.fuchss.matrix.tacit.tacitSearchResultBorder
import org.fuchss.matrix.tacit.tacitTextMuted
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry
import org.fuchss.matrix.tacit.views.room.list.dmInitials

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
                searchError = throwable.message ?: "Search failed."
            }
        )
        searchInProgress = false
    }

    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text(if (hasPresetUser) "Start Chat" else "Start Direct Message")
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasPresetUser) {
                    val targetDisplayName = presetDisplayName?.ifBlank { null } ?: userId
                    Text(
                        "Open a direct message with $targetDisplayName?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Search for a user or enter a Matrix user ID.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = userId,
                    onValueChange = { if (!hasPresetUser) userId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Matrix user ID") },
                    placeholder = { Text("@alice:example.org or display name") },
                    maxLines = 1,
                    enabled = !hasPresetUser,
                )
                if (!hasPresetUser && canSearchUsers && userId.trim().length >= 2) {
                    when {
                        searchInProgress -> {
                            Text(
                                "Searching users...",
                                style = MaterialTheme.typography.bodySmall,
                                color = tacitTextMuted,
                            )
                        }

                        searchResults.isNotEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tacitSearchResultBackground)
                                    .border(1.dp, tacitSearchResultBorder, RoundedCornerShape(10.dp))
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                searchResults.forEach { user ->
                                    val resolvedUserId = user.userId
                                    val resolvedDisplayName = user.displayName
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { userId = resolvedUserId }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ThemedUserAvatar(
                                            initials = dmInitials(resolvedDisplayName),
                                            image = null,
                                            size = 24.dp,
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(1.dp),
                                        ) {
                                            Text(
                                                text = resolvedDisplayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = resolvedUserId,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tacitTextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
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
                        "No active Matrix account available.",
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
                Text("Cancel")
            }
            ThemedButton(
                style = MaterialTheme.components.primaryButton,
                onClick = { onStartDirectMessage(userId) },
                enabled = !isStartingDirectMessage && canStartDirectMessage && userId.isNotBlank(),
            ) {
                Text(if (isStartingDirectMessage) "Starting..." else "Start Chat")
            }
        }
    }
}
