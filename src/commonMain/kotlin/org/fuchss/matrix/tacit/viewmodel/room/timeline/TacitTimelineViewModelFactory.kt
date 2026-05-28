package org.fuchss.matrix.tacit.viewmodel.room.timeline

import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OpenMentionCallback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.room.timeline.entry.ChannelMemberEntry
import org.fuchss.matrix.tacit.viewmodel.util.findExistingDirectMessageRoom
import org.fuchss.matrix.tacit.viewmodel.util.findOrCreateDM

internal object TacitTimelineViewModelFactory : TimelineViewModelFactory {
    override fun create(
        viewModelContext: MatrixClientViewModelContext,
        roomId: RoomId,
        onBack: () -> Unit,
        onOpenRoomSettings: () -> Unit,
        onOpenUserProfile: (UserId) -> Unit,
        onOpenMention: OpenMentionCallback,
        onOpenMetadata: (eventId: EventId) -> Unit,
    ): TimelineViewModel {
        val delegate = TimelineViewModelFactory.create(
            viewModelContext = viewModelContext,
            roomId = roomId,
            onBack = onBack,
            onOpenRoomSettings = onOpenRoomSettings,
            onOpenUserProfile = onOpenUserProfile,
            onOpenMention = onOpenMention,
            onOpenMetadata = onOpenMetadata,
        )
        return TacitTimelineViewModelImpl(delegate, viewModelContext, roomId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TacitTimelineViewModelImpl(
    private val delegate: TimelineViewModel,
    viewModelContext: MatrixClientViewModelContext,
    private val roomId: RoomId,
) : TacitTimelineViewModel, TimelineViewModel by delegate, MatrixClientViewModelContext by viewModelContext {

    private val avatarCache = mutableMapOf<String, ByteArray?>()
    private val _scrollToEndRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val scrollToEndRequests: Flow<Unit> = _scrollToEndRequests
    override val inputAreaViewModel: InputAreaViewModel =
        ScrollAwareInputAreaViewModel(delegate.inputAreaViewModel) {
            _scrollToEndRequests.tryEmit(Unit)
        }

    override val roomMembers: StateFlow<List<ChannelMemberEntry>> = matrixClient.user.getAll(roomId)
        .flattenValues()
        .map { members ->
            val joinedMembers = buildList {
                members.forEach { roomUser ->
                    if (roomUser.event.content.membership != Membership.JOIN) return@forEach
                    val userId = roomUser.userId
                    val avatarUri = roomUser.event.content.avatarUrl?.ifBlank { null }
                    val avatarImage = avatarUri?.let { uri ->
                        avatarCache.getOrPut(uri) {
                            matrixClient.media
                                .getThumbnail(uri = uri, width = 64L, height = 64L)
                                .getOrNull()
                                ?.toByteArray(maxSize = 512L * 1024L)
                        }
                    }
                    add(
                        ChannelMemberEntry(
                            userId = userId,
                            displayName = roomUser.name.ifBlank { userId.full },
                            isSelf = userId == matrixClient.userId,
                            avatarImage = avatarImage,
                        )
                    )
                }
                if (none { it.userId == matrixClient.userId }) {
                    add(
                        ChannelMemberEntry(
                            userId = matrixClient.userId,
                            displayName = matrixClient.userId.full,
                            isSelf = true,
                            avatarImage = null,
                        )
                    )
                }
            }
            joinedMembers.sortedWith(
                compareBy<ChannelMemberEntry> { !it.isSelf }.thenBy { it.displayName.lowercase() }
            )
        }
        .stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val typingMembers: StateFlow<List<ChannelMemberEntry>> = matrixClient.room.usersTyping
        .map { typingByRoom ->
            typingByRoom[roomId]?.users
                .orEmpty()
                .filterNot { it == matrixClient.userId }
                .sortedBy { it.full }
        }
        .distinctUntilChanged()
        .flatMapLatest { typingUsers ->
            if (typingUsers.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(typingUsers.map { typingUserId ->
                    matrixClient.user.getById(roomId, typingUserId).map { roomUser ->
                        val displayName = roomUser?.name?.ifBlank { null } ?: typingUserId.full
                        val avatarUri = roomUser?.avatarUrl?.ifBlank { null }
                        val avatarImage = avatarUri?.let { uri ->
                            avatarCache.getOrPut(uri) {
                                matrixClient.media
                                    .getThumbnail(uri = uri, width = 48L, height = 48L)
                                    .getOrNull()
                                    ?.toByteArray(maxSize = 512L * 1024L)
                            }
                        }
                        ChannelMemberEntry(
                            userId = typingUserId,
                            displayName = displayName,
                            isSelf = false,
                            avatarImage = avatarImage,
                        )
                    }
                }) { entries ->
                    entries.toList().sortedBy { it.displayName.lowercase() }
                }
            }
        }
        .stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val typingIndicatorText: StateFlow<String?> = typingMembers
        .map { typing ->
            when (typing.size) {
                0 -> null
                1 -> "${typing[0].displayName} is typing..."
                2 -> "${typing[0].displayName} and ${typing[1].displayName} are typing..."
                else -> "${typing[0].displayName}, ${typing[1].displayName} +${typing.size - 2} are typing..."
            }
        }
        .stateIn(coroutineScope, WhileSubscribed(), null)

    init {
        coroutineScope.launch {
            runCatching { matrixClient.user.loadMembers(roomId, false) }
        }
    }

    override suspend fun findExistingDirectMessageRoom(userId: UserId): RoomId? =
        userId.findExistingDirectMessageRoom(matrixClient)

    override suspend fun findOrCreateDirectMessageRoom(userId: UserId): RoomId? =
        userId.findOrCreateDM(matrixClient).getOrNull()
}

private class ScrollAwareInputAreaViewModel(
    private val delegate: InputAreaViewModel,
    private val onMessageSubmitted: () -> Unit,
) : InputAreaViewModel by delegate {
    override fun sendMessage() {
        val wasReadyToSend = delegate.isSendEnabled.value && !delegate.isReplace.value
        delegate.sendMessage()
        if (wasReadyToSend && delegate.textField.value.text.isEmpty()) {
            onMessageSubmitted()
        }
    }
}
