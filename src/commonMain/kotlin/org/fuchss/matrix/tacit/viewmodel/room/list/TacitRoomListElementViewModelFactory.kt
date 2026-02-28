package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListElementViewModelFactory
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal enum class TacitRoomType {
    DIRECT_MESSAGE,
    GROUP_CHANNEL,
    SPACE,
    UNKNOWN,
}

internal interface TacitRoomListElementViewModel : RoomListElementViewModel {
    val roomType: StateFlow<TacitRoomType>
    val isSpaceRoom: StateFlow<Boolean>
    val isGroupRoom: StateFlow<Boolean>
    val isDirectRoom: StateFlow<Boolean>
}

internal interface TacitRoomListElementViewModelFactory : RoomListElementViewModelFactory {
    override fun create(
        viewModelContext: MatrixClientViewModelContext,
        roomId: RoomId,
        onRoomSelected: () -> Unit,
        onCloseRoom: () -> Unit,
    ): RoomListElementViewModel {
        val delegate = RoomListElementViewModelFactory.create(
            viewModelContext = viewModelContext,
            roomId = roomId,
            onRoomSelected = onRoomSelected,
            onCloseRoom = onCloseRoom,
        )
        return TacitRoomListElementViewModelImpl(
            delegate = delegate,
            viewModelContext = viewModelContext,
            roomId = roomId,
        )
    }

    companion object : TacitRoomListElementViewModelFactory
}

private class TacitRoomListElementViewModelImpl(
    private val delegate: RoomListElementViewModel,
    viewModelContext: MatrixClientViewModelContext,
    roomId: RoomId,
) : TacitRoomListElementViewModel,
    RoomListElementViewModel by delegate,
    MatrixClientViewModelContext by viewModelContext {

    private val isSpaceByRoomState: StateFlow<Boolean?> = matrixClient.room.getById(roomId)
        .map { room -> room?.createEventContent?.type == RoomType.Space }
        .stateIn(coroutineScope, WhileSubscribed(), null)

    override val roomType: StateFlow<TacitRoomType> =
        combine(delegate.isLoaded, delegate.isDirect, isSpaceByRoomState) {
                isLoaded,
                isDirect,
                isSpace,
            ->
            when {
                !isLoaded -> TacitRoomType.UNKNOWN
                isSpace == true -> TacitRoomType.SPACE
                isDirect == true -> TacitRoomType.DIRECT_MESSAGE
                isDirect == false -> TacitRoomType.GROUP_CHANNEL
                else -> TacitRoomType.UNKNOWN
            }
        }.stateIn(coroutineScope, WhileSubscribed(), TacitRoomType.UNKNOWN)

    override val isSpaceRoom: StateFlow<Boolean> = roomType
        .map { it == TacitRoomType.SPACE }
        .stateIn(coroutineScope, WhileSubscribed(), false)

    override val isGroupRoom: StateFlow<Boolean> = roomType
        .map { it == TacitRoomType.GROUP_CHANNEL }
        .stateIn(coroutineScope, WhileSubscribed(), false)

    override val isDirectRoom: StateFlow<Boolean> = roomType
        .map { it == TacitRoomType.DIRECT_MESSAGE }
        .stateIn(coroutineScope, WhileSubscribed(), false)
}
