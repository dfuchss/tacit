package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.runtime.Composable
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry

@Composable
internal fun CreateCategoryDialogContainer(
    open: Boolean,
    inProgress: Boolean,
    selectedGuild: GuildEntry?,
    canCreateCategory: Boolean,
    onCreateCategory: (guild: GuildEntry, name: String) -> Unit,
    onSetOpen: (Boolean) -> Unit,
) {
    val guild = selectedGuild ?: return
    if (!open) return

    CreateCategoryDialog(
        guildName = guild.displayName ?: guild.roomId.full,
        isCreating = inProgress,
        canCreateCategory = canCreateCategory,
        onDismiss = { if (!inProgress) onSetOpen(false) },
        onCreateCategory = { name ->
            onCreateCategory(guild, name)
            onSetOpen(false)
        },
    )
}
