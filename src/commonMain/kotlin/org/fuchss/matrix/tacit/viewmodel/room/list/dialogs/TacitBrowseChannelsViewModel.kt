package org.fuchss.matrix.tacit.viewmodel.room.list.dialogs

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomDerived
import org.fuchss.matrix.tacit.viewmodel.room.list.browseCandidates
import org.fuchss.matrix.tacit.viewmodel.room.list.browseChannelEntries
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry
import org.fuchss.matrix.tacit.viewmodel.space.TacitGuildLayout
import org.fuchss.matrix.tacit.viewmodel.util.TacitErrorSource
import org.fuchss.matrix.tacit.viewmodel.util.TacitErrorState
import org.fuchss.matrix.tacit.viewmodel.util.TacitFailure
import org.fuchss.matrix.tacit.viewmodel.util.joinRoom
import org.fuchss.matrix.tacit.viewmodel.util.launchExclusive
import org.fuchss.matrix.tacit.viewmodel.util.tacitWhileSubscribed
import org.koin.core.component.get

/**
 * "Browse Rooms" of the selected guild: lists its channels (also those not joined yet, from the server's space
 * hierarchy) and joins one. Busy state is the room being joined, [joiningRoomId].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TacitBrowseChannelsViewModel(
    viewModelContext: ViewModelContext,
    rooms: Flow<List<TacitRoomDerived<*>>>,
    guildLayout: Flow<TacitGuildLayout>,
    hierarchyNames: Flow<Map<RoomId, String>>,
    selectedGuildClient: Flow<MatrixClient?>,
    private val guildClient: (GuildEntry) -> MatrixClient?,
    private val onRoomJoined: (RoomId) -> Unit,
) : ViewModelContext by viewModelContext, TacitErrorSource {
    private val tacitI18n = get<TacitI18n>()

    private val candidates: Flow<Set<RoomId>> =
        combine(guildLayout, hierarchyNames, ::browseCandidates).distinctUntilChanged()

    /** Store memberships of the candidates, for rooms the room list does not know. */
    private val memberships: Flow<Map<RoomId, Membership?>> =
        combine(selectedGuildClient.distinctUntilChanged(), candidates, ::Pair)
            .flatMapLatest { (client, candidateRoomIds) ->
                if (client == null || candidateRoomIds.isEmpty()) flowOf(emptyMap())
                else combine(candidateRoomIds.map { roomId ->
                    client.room.getById(roomId).map { room -> roomId to room?.membership }.distinctUntilChanged()
                }) { it.toMap() }
            }

    val channels: StateFlow<List<SpaceChannelEntry>> =
        combine(rooms, candidates, guildLayout, hierarchyNames, memberships, ::browseChannelEntries)
            .stateIn(coroutineScope, tacitWhileSubscribed, emptyList())

    private val errors = TacitErrorState()
    override val error: StateFlow<String?> = errors.error
    override val failure: StateFlow<TacitFailure?> = errors.failure

    override fun errorDismiss() = errors.clear()

    private val _joiningRoomId = MutableStateFlow<RoomId?>(null)
    val joiningRoomId: StateFlow<RoomId?> = _joiningRoomId.asStateFlow()

    fun joinChannel(guild: GuildEntry, channel: SpaceChannelEntry, onSuccess: () -> Unit) {
        if (!channel.isJoinable || channel.isJoined) return
        coroutineScope.launchExclusive(_joiningRoomId, idle = null, busy = channel.roomId) {
            errors.clear()
            val result = guildClient(guild)?.joinRoom(roomId = channel.roomId, viaServers = channel.via)
                ?: Result.failure(IllegalStateException(tacitI18n.tacitNoAccountForGuild()))
            result.fold(
                onSuccess = { roomId ->
                    onRoomJoined(roomId)
                    onSuccess()
                },
                onFailure = { throwable ->
                    errors.fail(tacitI18n.tacitJoinRoomFailed(tacitI18n.tacitErrorMessage(throwable)))
                },
            )
        }
    }
}
