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
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OpenMentionCallback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.koin.core.component.get
import org.fuchss.matrix.tacit.viewmodel.room.timeline.entry.ChannelMemberEntry
import org.fuchss.matrix.tacit.viewmodel.util.findExistingDirectMessageRoom
import org.fuchss.matrix.tacit.viewmodel.util.findOrCreateDM
import org.fuchss.matrix.tacit.viewmodel.util.loadAvatarThumbnail
import org.fuchss.matrix.tacit.viewmodel.util.mapConcurrently

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

private data class MemberAvatarInput(val userId: UserId, val name: String, val avatarUrl: String?)

@OptIn(ExperimentalCoroutinesApi::class)
private class TacitTimelineViewModelImpl(
    private val delegate: TimelineViewModel,
    viewModelContext: MatrixClientViewModelContext,
    private val roomId: RoomId,
) : TacitTimelineViewModel, TimelineViewModel by delegate, MatrixClientViewModelContext by viewModelContext {

    private val tacitI18n = get<TacitI18n>()
    private val _scrollToEndRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val caughtUp = MutableStateFlow(false)
    private var lastCaughtUpReceiptEventId: EventId? = null

    override val scrollToEndRequests: Flow<Unit> = _scrollToEndRequests
    override val inputAreaViewModel: InputAreaViewModel =
        ScrollAwareInputAreaViewModel(delegate.inputAreaViewModel) {
            _scrollToEndRequests.tryEmit(Unit)
        }

    override val roomMembers: StateFlow<List<ChannelMemberEntry>> = matrixClient.user.getAll(roomId)
        .flattenValues()
        .map { members ->
            members
                .filter { it.event.content.membership == Membership.JOIN }
                .map { MemberAvatarInput(it.userId, it.name, it.event.content.avatarUrl) }
        }
        .distinctUntilChanged()
        .mapLatest { members ->
            val joinedMembers = buildList {
                addAll(members.mapConcurrently { member ->
                    val userId = member.userId
                    ChannelMemberEntry(
                        userId = userId,
                        displayName = member.name.ifBlank { userId.full },
                        isSelf = userId == matrixClient.userId,
                        avatarImage = matrixClient.loadAvatarThumbnail(member.avatarUrl, 64L),
                    )
                })
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
                    matrixClient.user.getById(roomId, typingUserId)
                        .map { roomUser -> roomUser?.name to roomUser?.avatarUrl }
                        .distinctUntilChanged()
                        .mapLatest { (name, avatarUrl) ->
                        val displayName = name?.ifBlank { null } ?: typingUserId.full
                        val avatarImage = matrixClient.loadAvatarThumbnail(avatarUrl, 48L)
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
            tacitI18n.tacitTypingIndicator(typing.map { it.displayName })
        }
        .stateIn(coroutineScope, WhileSubscribed(), null)

    init {
        coroutineScope.launch {
            runCatching { matrixClient.user.loadMembers(roomId, false) }
        }
        // Upstream only moves the receipt to the last visible *message*, but trixnity clears the
        // notification count only when the receipt sits exactly on `room.lastEventId`, which may be a
        // trailing state event. So once caught up, move the receipt to that event as well.
        coroutineScope.launch {
            caughtUp
                .flatMapLatest { isCaughtUp ->
                    if (isCaughtUp) matrixClient.room.getById(roomId).map { it?.lastEventId }.filterNotNull().distinctUntilChanged()
                    else emptyFlow()
                }
                .collect { markReceiptOn(it) }
        }
    }

    override fun setCaughtUp(caughtUp: Boolean) {
        this.caughtUp.value = caughtUp
    }

    private suspend fun markReceiptOn(eventId: EventId) {
        if (eventId == lastCaughtUpReceiptEventId) return
        val ownReceipts = matrixClient.user.getReceiptsById(roomId, matrixClient.userId).first()?.receipts.orEmpty()
        if (ownReceipts.values.any { it.eventId == eventId }) {
            lastCaughtUpReceiptEventId = eventId
            return
        }
        val isPublic = get<MatrixMessengerSettingsHolder>().get(userId).first()?.base?.readMarkerIsPublic ?: false
        lastCaughtUpReceiptEventId = eventId
        matrixClient.api.room
            .setReadMarkers(roomId, fullyRead = eventId, read = eventId.takeIf { isPublic }, privateRead = eventId)
            .onFailure { lastCaughtUpReceiptEventId = null }
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
