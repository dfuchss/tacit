package org.fuchss.matrix.tacit.viewmodel.room.timeline

import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OpenMentionCallback
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

private class TacitTimelineViewModelImpl(
    private val delegate: TimelineViewModel,
    viewModelContext: MatrixClientViewModelContext,
    private val roomId: RoomId,
) : TacitTimelineViewModel, TimelineViewModel by delegate, MatrixClientViewModelContext by viewModelContext {

    private val avatarCache = mutableMapOf<String, ByteArray?>()

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
