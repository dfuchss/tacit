package org.fuchss.matrix.tacit.viewmodel.util

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
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
            powerLevelContentOverride = PowerLevelsEventContent(
                users = mapOf(this.userId to 100),
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
    val roomSnapshots = buildList {
        repeat(2) {
            val snapshot = withTimeoutOrNull(8.seconds.inWholeMilliseconds) {
                matrixClient.room.getAll().flattenValues().first()
            }
            if (snapshot != null) add(snapshot)
        }
    }
    if (roomSnapshots.isEmpty()) {
        return null
    }

    for (rooms in roomSnapshots) {
        val candidateRooms = rooms.filter { room ->
            room.membership == Membership.JOIN && room.createEventContent?.type != RoomType.Space
        }

        for (room in candidateRooms) {
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
            val hasTarget = activeMemberIds.contains(this)
            val hasSelf = activeMemberIds.contains(matrixClient.userId)
            if (room.isDirect && hasTarget && hasSelf) {
                return room.roomId
            }
        }
    }

    return null
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
                users = mapOf(matrixClient.userId to 100L, targetUserId to 100L)
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
