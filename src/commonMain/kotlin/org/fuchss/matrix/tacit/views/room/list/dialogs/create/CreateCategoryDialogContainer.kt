package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateCategoryDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    error: String?,
    selectedGuild: GuildEntry?,
    canCreateCategory: Boolean,
    onCreateCategory: (guild: GuildEntry, name: String, onSuccess: () -> Unit) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return
    val submission = rememberTacitDialogSubmission(error)

    CreateCategoryDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isCreating = inProgress,
        canCreateCategory = canCreateCategory,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateCategory = { name ->
            submission.submit()
            onCreateCategory(guild, name) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
