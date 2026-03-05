package org.fuchss.matrix.tacit.viewmodel.room.settings

import de.connect2x.lognity.api.logger.error
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.AvatarEventContent
import de.connect2x.trixnity.messenger.util.FileDescriptor
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.room.settings.ChangeAvatarViewModelImpl
import de.connect2x.trixnity.messenger.viewmodel.room.settings.ChangeRoomAvatarViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.settings.ChangeRoomAvatarViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.settings.OpenAvatarCutterCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface TacitChangeRoomAvatarViewModel : ChangeRoomAvatarViewModel {
    fun removeRoomAvatar()
}

object TacitChangeRoomAvatarViewModelFactory : ChangeRoomAvatarViewModelFactory {
    override fun create(
        viewModelContext: MatrixClientViewModelContext,
        selectedRoomId: RoomId,
        onOpenAvatarCutter: OpenAvatarCutterCallback,
    ): ChangeRoomAvatarViewModel {
        return TacitChangeRoomAvatarViewModelImpl(
            viewModelContext = viewModelContext,
            selectedRoomId = selectedRoomId,
            onOpenAvatarCutter = onOpenAvatarCutter,
        )
    }
}

private class TacitChangeRoomAvatarViewModelImpl(
    viewModelContext: MatrixClientViewModelContext,
    private val selectedRoomId: RoomId,
    onOpenAvatarCutter: OpenAvatarCutterCallback,
) : TacitChangeRoomAvatarViewModel, MatrixClientViewModelContext by viewModelContext {

    private val delegate = ChangeAvatarViewModelImpl(
        viewModelContext = viewModelContext,
        selectedRoomId = selectedRoomId,
        onOpenAvatarCutter = onOpenAvatarCutter,
    )

    override val canChangeRoomAvatar: StateFlow<Boolean> = delegate.canChangeRoomAvatar
    override val avatar: StateFlow<ByteArray?> = delegate.avatar
    override val initials: StateFlow<String> = delegate.initials
    override val openImageSelector: MutableStateFlow<Boolean> = delegate.openImageSelector

    override fun openAvatarCutter(file: FileDescriptor) = delegate.openAvatarCutter(file)

    override fun removeRoomAvatar() {
        if (!canChangeRoomAvatar.value) return
        coroutineScope.launch {
            matrixClient.api.room.sendStateEvent(
                roomId = selectedRoomId,
                eventContent = AvatarEventContent(url = null),
            ).onFailure {
                log.error(it) { "Cannot remove room avatar." }
            }
        }
    }
}
