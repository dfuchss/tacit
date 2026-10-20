package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.messenger.viewmodel.TextFieldViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.DmFilter
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitScrollPosition
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

/** What the room list view can do; stable for the whole composition, so it never causes recompositions. */
@Stable
internal interface RoomListActions {
    /** The search field's two-way binding. */
    val searchTerm: TextFieldViewModel

    fun selectGuild(guild: GuildEntry?)
    fun reorderGuild(fromIndex: Int, toIndex: Int)
    fun reorderCategory(fromIndex: Int, toIndex: Int)
    fun selectRoom(roomId: RoomId)
    fun createNewRoom()
    fun selectDmFilter(filter: DmFilter)
    fun dismissError()

    fun openCreateGuild()

    /** Opens the dialog that creates a room in the current mode: a DM in the DM home, else a channel of the guild. */
    fun openCreateRoom()
    fun openCreateCategory()
    fun openCreateGroupChannel()
    fun openBrowseChannels()
    fun openInviteToGuild()

    /** Opens the settings of a guild or category. */
    fun openSpaceSettings(spaceRoomId: RoomId)

    fun acceptSelectedGuildInvite()
    fun declineSelectedGuildInvite()

    fun restoreScrollPosition(key: String): TacitScrollPosition?
    fun saveScrollPosition(key: String, position: TacitScrollPosition)
}

/** Which of the room list's dialogs are open. */
@Stable
internal class RoomListDialogState {
    var createGuild by mutableStateOf(false)
    var createChannel by mutableStateOf(false)
    var browseChannels by mutableStateOf(false)
    var inviteToGuild by mutableStateOf(false)
    var createDirectMessage by mutableStateOf(false)
    var createGroupChannel by mutableStateOf(false)
    var createCategory by mutableStateOf(false)
}

/** [RoomListActions] on top of the view model; reads the view model's latest state when an action fires. */
internal class TacitRoomListViewActions(
    private val viewModel: TacitRoomListViewModel,
    private val i18n: TacitI18nView,
    private val dialogs: RoomListDialogState,
) : RoomListActions {
    override val searchTerm: TextFieldViewModel get() = viewModel.searchTerm

    override fun selectGuild(guild: GuildEntry?) = viewModel.selectGuild(guild)
    override fun reorderGuild(fromIndex: Int, toIndex: Int) = viewModel.reorderGuild(fromIndex, toIndex)
    override fun reorderCategory(fromIndex: Int, toIndex: Int) = viewModel.reorderCategory(fromIndex, toIndex)
    override fun selectRoom(roomId: RoomId) = viewModel.selectRoom(roomId)
    override fun createNewRoom() = viewModel.createNewRoom()
    override fun selectDmFilter(filter: DmFilter) = viewModel.selectDmFilter(filter)
    override fun dismissError() = viewModel.errorDismiss()

    /** Every dialog starts without the error of a previous action. */
    private inline fun openDialog(open: RoomListDialogState.() -> Unit) {
        viewModel.errorDismiss()
        dialogs.open()
    }

    override fun openCreateGuild() = openDialog { createGuild = true }

    override fun openCreateRoom() = openDialog {
        if (viewModel.selectedGuild.value == null) createDirectMessage = true else createChannel = true
    }

    override fun openCreateCategory() = openDialog { createCategory = true }
    override fun openCreateGroupChannel() = openDialog { createGroupChannel = true }
    override fun openBrowseChannels() = openDialog { browseChannels = true }
    override fun openInviteToGuild() = openDialog { inviteToGuild = true }

    override fun openSpaceSettings(spaceRoomId: RoomId) = viewModel.openSpaceSettings(spaceRoomId)

    private fun answerSelectedGuildInvite(answer: (GuildEntry) -> Unit) {
        val state = viewModel.uiState.value
        val targetGuild = state.selectedGuildInviteFallback
        if (targetGuild == null) {
            viewModel.reportError(i18n.tacitNoAccountForGuildInviteError())
            return
        }
        if (state.status.guildInviteActionInProgress) return
        answer(targetGuild)
    }

    override fun acceptSelectedGuildInvite() = answerSelectedGuildInvite(viewModel::acceptGuildInvite)
    override fun declineSelectedGuildInvite() = answerSelectedGuildInvite(viewModel::declineGuildInvite)

    override fun restoreScrollPosition(key: String): TacitScrollPosition? = viewModel.scrollMemory[key]

    override fun saveScrollPosition(key: String, position: TacitScrollPosition) {
        viewModel.scrollMemory[key] = position
    }
}
