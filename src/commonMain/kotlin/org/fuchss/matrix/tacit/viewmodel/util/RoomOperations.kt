package org.fuchss.matrix.tacit.viewmodel.util

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.clientserverapi.model.room.CreateRoom
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.EventType
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.room.*
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import kotlin.time.Duration.Companion.seconds

private const val ROOM_OPERATION_TIMEOUT_MS = 20_000L

internal suspend fun MatrixClient.createGuild(
    guildName: String,
    guildTopic: String,
    createDefaultChannel: Boolean,
): Result<GuildEntry> {
    val trimmedName = guildName.trim()
    if (trimmedName.isBlank()) {
        return Result.failure(IllegalArgumentException("Guild name is required"))
    }

    val spaceId = withOperationTimeout("Guild creation") {
        this.api.room.createRoom(
            name = trimmedName,
            topic = guildTopic.ifBlank { null },
            isDirect = false,
            preset = CreateRoom.Request.Preset.PRIVATE,
            creationContent = CreateEventContent(type = RoomType.Space),
            // Do not list the creator in `users`: the server grants the creator full power anyway and room
            // version 12 (MSC4289) rejects creators in that field.
            powerLevelContentOverride = PowerLevelsEventContent(
                events = mapOf(
                    EventType(ChildEventContent::class, "m.space.child") to 0L,
                )
            ),
        )
    }.getOrElse { return Result.failure(it) }

    val guild = GuildEntry(
        roomId = spaceId,
        userId = this.userId,
        displayName = trimmedName,
        avatarUri = null,
        membership = Membership.JOIN,
    )

    if (createDefaultChannel) {
        guild.createChannel(
            matrixClient = this,
            channelName = "general",
            channelTopic = "General discussion",
        ).getOrElse { return Result.failure(it) }
    }
    return Result.success(guild)
}

internal suspend fun GuildEntry.createChannel(
    matrixClient: MatrixClient,
    channelName: String,
    channelTopic: String,
): Result<RoomId> {
    val trimmedName = channelName.trim()
    if (trimmedName.isBlank()) {
        return Result.failure(IllegalArgumentException("Room name is required"))
    }
    val roomId = withOperationTimeout("Room creation") {
        matrixClient.api.room.createRoom(
            name = trimmedName,
            topic = channelTopic.ifBlank { null },
            isDirect = false,
            preset = CreateRoom.Request.Preset.PRIVATE,
            initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
        )
    }.getOrElse { return Result.failure(it) }

    val via = setOf(matrixClient.userId.domain)
    withOperationTimeout("Link room to guild") {
        matrixClient.api.room.sendStateEvent(
            roomId = this.roomId,
            eventContent = ChildEventContent(via = via, suggested = true),
            stateKey = roomId.full,
        )
    }.getOrElse { return Result.failure(it) }

    withOperationTimeout("Link guild to room") {
        matrixClient.api.room.sendStateEvent(
            roomId = roomId,
            eventContent = ParentEventContent(canonical = true, via = via),
            stateKey = this.roomId.full,
        )
    }.getOrElse { return Result.failure(it) }

    withOperationTimeout("Configure room join rules") {
        matrixClient.api.room.sendStateEvent(
            roomId = roomId,
            eventContent = JoinRulesEventContent(
                joinRule = JoinRulesEventContent.JoinRule.Restricted,
                allow = setOf(
                    JoinRulesEventContent.AllowCondition(
                        roomId = this.roomId,
                        type = JoinRulesEventContent.AllowCondition.AllowConditionType.RoomMembership,
                    )
                ),
            ),
        )
    }.getOrElse { return Result.failure(it) }

    return Result.success(roomId)
}

internal suspend fun GuildEntry.createCategory(
    matrixClient: MatrixClient,
    categoryName: String,
): Result<RoomId> {
    val trimmedName = categoryName.trim()
    if (trimmedName.isBlank()) {
        return Result.failure(IllegalArgumentException("Category name is required"))
    }

    val categoryRoomId = withOperationTimeout("Category creation") {
        matrixClient.api.room.createRoom(
            name = trimmedName,
            isDirect = false,
            preset = CreateRoom.Request.Preset.PRIVATE,
            creationContent = CreateEventContent(type = RoomType.Space),
            powerLevelContentOverride = PowerLevelsEventContent(
                events = mapOf(
                    EventType(ChildEventContent::class, "m.space.child") to 0L,
                ),
            ),
        )
    }.getOrElse { return Result.failure(it) }

    val via = setOf(matrixClient.userId.domain)
    withOperationTimeout("Link category to guild") {
        matrixClient.api.room.sendStateEvent(
            roomId = this.roomId,
            eventContent = ChildEventContent(via = via, suggested = false),
            stateKey = categoryRoomId.full,
        )
    }.getOrElse { return Result.failure(it) }

    withOperationTimeout("Link guild to category") {
        matrixClient.api.room.sendStateEvent(
            roomId = categoryRoomId,
            eventContent = ParentEventContent(canonical = false, via = via),
            stateKey = this.roomId.full,
        )
    }.getOrElse { return Result.failure(it) }

    withOperationTimeout("Configure category join rules") {
        matrixClient.api.room.sendStateEvent(
            roomId = categoryRoomId,
            eventContent = JoinRulesEventContent(
                joinRule = JoinRulesEventContent.JoinRule.Restricted,
                allow = setOf(
                    JoinRulesEventContent.AllowCondition(
                        roomId = this.roomId,
                        type = JoinRulesEventContent.AllowCondition.AllowConditionType.RoomMembership,
                    )
                ),
            ),
        )
    }.getOrElse { return Result.failure(it) }

    return Result.success(categoryRoomId)
}

internal suspend fun moveRoomToCategory(
    matrixClient: MatrixClient,
    roomId: RoomId,
    targetCategoryRoomId: RoomId?,
    // Every category that currently lists the room as a child. All of them except the target are unlinked, so the
    // room never ends up in two categories (and a room already listed twice is repaired by the move).
    currentCategoryRoomIds: Set<RoomId>,
): Result<Unit> {
    val guildVia = setOf(matrixClient.userId.domain)

    for (currentCategoryRoomId in currentCategoryRoomIds - setOfNotNull(targetCategoryRoomId)) {
        withOperationTimeout("Unlink room from previous category") {
            matrixClient.api.room.sendStateEvent(
                roomId = currentCategoryRoomId,
                eventContent = ChildEventContent(via = emptySet(), suggested = false),
                stateKey = roomId.full,
            )
        }.getOrElse { return Result.failure(it) }
    }

    if (targetCategoryRoomId == null) {
        return Result.success(Unit)
    }

    withOperationTimeout("Link room to category") {
        matrixClient.api.room.sendStateEvent(
            roomId = targetCategoryRoomId,
            eventContent = ChildEventContent(via = guildVia, suggested = false),
            stateKey = roomId.full,
        )
    }.getOrElse { return Result.failure(it) }

    withOperationTimeout("Link category to room") {
        matrixClient.api.room.sendStateEvent(
            roomId = roomId,
            eventContent = ParentEventContent(canonical = false, via = guildVia),
            stateKey = targetCategoryRoomId.full,
        )
    }.getOrElse { return Result.failure(it) }

    return Result.success(Unit)
}

internal suspend fun MatrixClient.createGroupChannel(
    roomName: String,
    roomTopic: String,
): Result<RoomId> {
    val trimmedName = roomName.trim()
    if (trimmedName.isBlank()) {
        return Result.failure(IllegalArgumentException("Room name is required"))
    }
    val roomId = withOperationTimeout("Group chat creation") {
        this.api.room.createRoom(
            name = trimmedName,
            topic = roomTopic.ifBlank { null },
            isDirect = false,
            preset = CreateRoom.Request.Preset.PRIVATE,
            initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
        )
    }.getOrElse { return Result.failure(it) }

    return Result.success(roomId)
}

internal suspend fun GuildEntry.inviteMember(
    matrixClient: MatrixClient,
    userId: String,
    reason: String,
): Result<Unit> {
    val trimmedUserId = userId.trim()
    if (trimmedUserId.isBlank()) {
        return Result.failure(IllegalArgumentException("Matrix user ID is required"))
    }
    val invitee = runCatching { UserId(trimmedUserId) }
        .getOrElse {
            return Result.failure(IllegalArgumentException("Enter a valid Matrix user ID like @alice:example.org"))
        }
    return matrixClient.api.room.inviteUser(
        roomId = this.roomId,
        userId = invitee,
        reason = reason.trim().ifBlank { null },
    )
}

internal suspend fun MatrixClient.acceptRoomInvite(
    roomId: RoomId,
): Result<RoomId> {
    return this.api.room.joinRoom(roomId = roomId)
}

internal suspend fun MatrixClient.declineRoomInvite(
    roomId: RoomId,
): Result<Unit> {
    return this.api.room.leaveRoom(roomId = roomId)
}

internal suspend fun MatrixClient.joinRoom(
    roomId: RoomId,
    viaServers: Set<String>,
): Result<RoomId> {
    return api.room.joinRoom(
        roomId = roomId,
        via = viaServers.ifEmpty { null },
    )
}


internal suspend fun UserId.findOrCreateDM(
    matrixClient: MatrixClient
): Result<RoomId> {

    val existingDirect = this.findExistingDirectMessageRoom(matrixClient)
    if (existingDirect != null) {
        return Result.success(existingDirect)
    }

    return createDirectMessageRoom(
        matrixClient = matrixClient,
        targetUserId = this,
    )
}

internal suspend fun UserId.findExistingDirectMessageRoom(
    matrixClient: MatrixClient
): RoomId? {
    // One retry in case the room store is not ready yet on the first attempt.
    val rooms = withTimeoutOrNull(8.seconds.inWholeMilliseconds) {
        matrixClient.room.getAll().flattenValues().first()
    } ?: withTimeoutOrNull(8.seconds.inWholeMilliseconds) {
        matrixClient.room.getAll().flattenValues().first()
    } ?: return null

    // Only direct rooms (m.direct) can match, so members are loaded for those alone instead of for every joined room.
    for (room in rooms.directMessageCandidates()) {
        runCatching {
            withTimeout(8.seconds.inWholeMilliseconds) {
                matrixClient.user.loadMembers(room.roomId, false)
            }
        }
        val members = withTimeoutOrNull(8.seconds.inWholeMilliseconds) {
            matrixClient.user.getAll(room.roomId)
                .flattenValues()
                .first()
        } ?: continue
        val activeMemberIds = members
            .filter {
                it.event.content.membership == Membership.JOIN || it.event.content.membership == Membership.INVITE
            }
            .map { it.userId }
            .toSet()
        if (activeMemberIds.contains(this) && activeMemberIds.contains(matrixClient.userId)) {
            return room.roomId
        }
    }

    return null
}

/** Joined, non-space rooms flagged as direct: the only rooms that can be an existing DM with someone. */
internal fun Iterable<Room>.directMessageCandidates(): List<Room> = filter { room ->
    room.isDirect && room.membership == Membership.JOIN && room.createEventContent?.type != RoomType.Space
}

private suspend fun createDirectMessageRoom(
    matrixClient: MatrixClient,
    targetUserId: UserId,
): Result<RoomId> {
    val roomId = withOperationTimeout("Direct message creation") {
        matrixClient.api.room.createRoom(
            isDirect = true,
            preset = CreateRoom.Request.Preset.PRIVATE,
            invite = setOf(targetUserId),
            powerLevelContentOverride = PowerLevelsEventContent(
                users = mapOf(targetUserId to 100L) // the creator must not be listed (room version 12)
            ),
            initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
        )
    }.getOrElse { return Result.failure(it) }

    return Result.success(roomId)
}

private suspend fun <T> withOperationTimeout(
    operation: String,
    block: suspend () -> Result<T>,
): Result<T> = try {
    withTimeout(ROOM_OPERATION_TIMEOUT_MS) {
        block()
    }
} catch (timeout: TimeoutCancellationException) {
    Result.failure(IllegalStateException("$operation timed out. Please try again.", timeout))
}
