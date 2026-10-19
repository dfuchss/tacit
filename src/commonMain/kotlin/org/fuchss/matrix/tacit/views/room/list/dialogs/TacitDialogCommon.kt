package org.fuchss.matrix.tacit.views.room.list.dialogs

import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitSizes
import org.fuchss.matrix.tacit.viewmodel.util.initialsOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitCardSurface
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry
import org.fuchss.matrix.tacit.viewmodel.util.UserSearchState
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get

@Composable
internal fun TacitDialogHeroCard(
    eyebrow: String? = null,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(TacitShapes.section)
            .background(
                Brush.horizontalGradient(
                    listOf(MaterialTheme.tacit.surface, MaterialTheme.tacit.surfaceAlt, MaterialTheme.tacit.surface)
                )
            )
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.border.copy(alpha = 0.8f), TacitShapes.section)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            eyebrow?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.tacit.textMuted,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.tacit.text,
            )
        }
    }
}

@Composable
internal fun TacitDialogSearchResultsContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TacitShapes.card)
            .background(MaterialTheme.tacit.surface)
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, TacitShapes.card)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
internal fun TacitUserDirectoryRow(
    user: UserDirectoryEntry,
    selected: Boolean,
    selectedLabel: String,
    onClick: () -> Unit,
) {
    TacitCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(0.dp),
        shape = TacitShapes.compact,
        backgroundColor = MaterialTheme.tacit.surface,
        borderColor = if (selected) MaterialTheme.tacit.accent.copy(alpha = TacitAlpha.borderEmphasis) else MaterialTheme.tacit.border.copy(alpha = 0.55f),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemedUserAvatar(
                initials = initialsOf(user.displayName, singleWordLength = 1),
                image = null,
                size = 24.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.userId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.tacit.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Hands the dialog's user ID field to the view model's user search; the search stays idle unless [enabled]. */
@Composable
internal fun TacitUserSearchEffect(query: String, enabled: Boolean, onSearch: (query: String) -> Unit) {
    LaunchedEffect(query, enabled) { onSearch(if (enabled) query else "") }
}

/** The user search's progress, hits (click one to pick it) or failure below a dialog's user ID field. */
@Composable
internal fun TacitUserSearchResults(state: UserSearchState, selectedUserId: String, onSelect: (userId: String) -> Unit) {
    val i18n = DI.get<TacitI18nView>()
    when (state) {
        UserSearchState.Idle -> Unit

        UserSearchState.Searching -> Text(
            i18n.tacitSearchingUsers(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.tacit.textMuted,
        )

        is UserSearchState.Results -> if (state.users.isNotEmpty()) {
            TacitDialogSearchResultsContainer {
                state.users.forEach { user ->
                    TacitUserDirectoryRow(
                        user = user,
                        selected = selectedUserId == user.userId,
                        selectedLabel = i18n.actionOk(),
                        onClick = { onSelect(user.userId) },
                    )
                }
            }
        }

        is UserSearchState.Failed -> Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Shows why the dialog's last submit failed; the dialog stays open so the input is not lost. */
@Composable
internal fun TacitDialogSubmitError(message: String?) {
    if (message == null) return
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * Tracks a dialog's submit so the dialog can stay open while the action runs, close only on success (via the
 * action's `onSuccess` callback), and keep showing the failure reported through the view model's error.
 */
@Stable
internal class TacitDialogSubmission {
    var error by mutableStateOf<String?>(null)
        private set
    private var submitted by mutableStateOf(false)

    fun submit() {
        submitted = true
        error = null
    }

    fun onError(message: String?) {
        if (submitted && message != null) error = message
    }
}

@Composable
internal fun rememberTacitDialogSubmission(error: String?): TacitDialogSubmission {
    val submission = remember { TacitDialogSubmission() }
    LaunchedEffect(error) { submission.onError(error) }
    return submission
}
