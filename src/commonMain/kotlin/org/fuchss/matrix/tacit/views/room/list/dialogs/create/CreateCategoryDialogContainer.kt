package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.fuchss.matrix.tacit.viewmodel.room.list.dialogs.TacitCreateCategoryViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.views.room.list.dialogs.rememberTacitDialogSubmission

@Composable
internal fun CreateCategoryDialogContainer(
    open: Boolean,
    viewModel: TacitCreateCategoryViewModel,
    selectedGuild: GuildEntry?,
    canCreateCategory: Boolean,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return
    val inProgress = viewModel.isCreating.collectAsState().value
    val submission = rememberTacitDialogSubmission(viewModel.error.collectAsState().value)

    CreateCategoryDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isCreating = inProgress,
        canCreateCategory = canCreateCategory,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateCategory = { name ->
            submission.submit()
            viewModel.createCategory(guild, name) { onSetOpen(false) }
        },
        submitError = submission.error,
    )
}
