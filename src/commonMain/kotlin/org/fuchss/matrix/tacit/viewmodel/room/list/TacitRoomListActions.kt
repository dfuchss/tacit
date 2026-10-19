package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry
import org.fuchss.matrix.tacit.viewmodel.util.*

internal class TacitRoomListActions(
    private val scope: CoroutineScope,
    private val reportError: (String?) -> Unit,
    private val onGuildSelected: (GuildEntry?) -> Unit,
    private val onRoomSelected: (RoomId) -> Unit,
    private val resolveGuildClient: (GuildEntry) -> MatrixClient?,
    private val resolvePreferredCreationClient: () -> MatrixClient?,
) {
    private val _createGuildInProgress = MutableStateFlow(false)
    val createGuildInProgress: StateFlow<Boolean> = _createGuildInProgress

    private val _createChannelInProgress = MutableStateFlow(false)
    val createChannelInProgress: StateFlow<Boolean> = _createChannelInProgress

    private val _joiningChannelRoomId = MutableStateFlow<RoomId?>(null)
    val joiningChannelRoomId: StateFlow<RoomId?> = _joiningChannelRoomId

    private val _inviteToGuildInProgress = MutableStateFlow(false)
    val inviteToGuildInProgress: StateFlow<Boolean> = _inviteToGuildInProgress

    private val _createDirectMessageInProgress = MutableStateFlow(false)
    val createDirectMessageInProgress: StateFlow<Boolean> = _createDirectMessageInProgress

    private val _createGroupChannelInProgress = MutableStateFlow(false)
    val createGroupChannelInProgress: StateFlow<Boolean> = _createGroupChannelInProgress

    private val _createCategoryInProgress = MutableStateFlow(false)
    val createCategoryInProgress: StateFlow<Boolean> = _createCategoryInProgress

    private val _reorderCategoriesInProgress = MutableStateFlow(false)
    val reorderCategoriesInProgress: StateFlow<Boolean> = _reorderCategoriesInProgress

    private val _guildInviteActionInProgress = MutableStateFlow(false)
    val guildInviteActionInProgress: StateFlow<Boolean> = _guildInviteActionInProgress

    fun createGuild(name: String, topic: String, createDefaultChannel: Boolean, onSuccess: () -> Unit) {
        scope.launchExclusive(_createGuildInProgress) {
            reportError(null)
            val result = resolvePreferredCreationClient()
                ?.createGuild(
                    guildName = name,
                    guildTopic = topic,
                    createDefaultChannel = createDefaultChannel,
                )
                ?: Result.failure(IllegalStateException("No active Matrix account available for guild creation."))
            result.fold(
                onSuccess = { guild ->
                    onGuildSelected(guild)
                    onSuccess()
                },
                onFailure = { throwable ->
                    reportError("Could not create guild: ${throwable.message ?: "Unknown error"}")
                },
            )
        }
    }

    fun createChannel(guild: GuildEntry, name: String, topic: String, onSuccess: () -> Unit) {
        scope.launchExclusive(_createChannelInProgress) {
            reportError(null)
            val result = resolveGuildClient(guild)?.let { client ->
                guild.createChannel(
                    matrixClient = client,
                    channelName = name,
                    channelTopic = topic,
                )
            } ?: Result.failure(IllegalStateException("Could not resolve active guild context."))
            result.fold(
                onSuccess = { roomId ->
                    onRoomSelected(roomId)
                    onSuccess()
                },
                onFailure = { throwable ->
                    reportError("Could not create room: ${throwable.message ?: "Unknown error"}")
                },
            )
        }
    }

    fun joinChannel(guild: GuildEntry, channel: SpaceChannelEntry, onSuccess: () -> Unit) {
        if (!channel.isJoinable || channel.isJoined) return
        scope.launchExclusive(_joiningChannelRoomId, idle = null, busy = channel.roomId) {
            reportError(null)
            val result = resolveGuildClient(guild)?.joinRoom(
                roomId = channel.roomId,
                viaServers = channel.via,
            ) ?: Result.failure(IllegalStateException("No account available for this guild."))
            result.fold(
                onSuccess = { roomId ->
                    onRoomSelected(roomId)
                    onSuccess()
                },
                onFailure = { throwable ->
                    reportError("Could not join room: ${throwable.message ?: "Unknown error"}")
                },
            )
        }
    }

    fun inviteUserToGuild(guild: GuildEntry, userId: String, reason: String, onSuccess: () -> Unit) {
        scope.launchExclusive(_inviteToGuildInProgress) {
            reportError(null)
            val result = resolveGuildClient(guild)?.let { client ->
                guild.inviteMember(
                    matrixClient = client,
                    userId = userId,
                    reason = reason,
                )
            } ?: Result.failure(IllegalStateException("Could not resolve active guild context."))
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { throwable ->
                    reportError("Could not send invite: ${throwable.message ?: "Unknown error"}")
                },
            )
        }
    }

    fun startDirectMessage(userId: String, onSuccess: () -> Unit) {
        scope.launchExclusive(_createDirectMessageInProgress) {
            reportError(null)
            val result = when {
                !UserId.isValid(userId) -> Result.failure(IllegalArgumentException("No valid userId provided: '$userId'"))
                else -> resolvePreferredCreationClient()?.let { client ->
                    UserId(userId).findOrCreateDM(client)
                } ?: Result.failure(IllegalStateException("No active Matrix account available."))
            }
            result.fold(
                onSuccess = { roomId ->
                    onRoomSelected(roomId)
                    onSuccess()
                },
                onFailure = { throwable ->
                    reportError("Could not start direct message: ${throwable.message ?: "Unknown error"}")
                },
            )
        }
    }

    fun createGroupChannel(name: String, topic: String, onSuccess: () -> Unit) {
        scope.launchExclusive(_createGroupChannelInProgress) {
            reportError(null)
            val result = resolvePreferredCreationClient()?.createGroupChannel(
                roomName = name,
                roomTopic = topic,
            ) ?: Result.failure(IllegalStateException("No active Matrix account available."))
            result.fold(
                onSuccess = { roomId ->
                    onRoomSelected(roomId)
                    onSuccess()
                },
                onFailure = { throwable ->
                    reportError("Could not create group chat: ${throwable.message ?: "Unknown error"}")
                },
            )
        }
    }

    fun createCategory(guild: GuildEntry, name: String, onSuccess: () -> Unit) {
        scope.launchExclusive(_createCategoryInProgress) {
            reportError(null)
            val result = resolveGuildClient(guild)?.let { client ->
                guild.createCategory(
                    matrixClient = client,
                    categoryName = name,
                )
            } ?: Result.failure(IllegalStateException("Could not resolve active guild context."))
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { throwable ->
                    reportError("Could not create category: ${throwable.message ?: "Unknown error"}")
                },
            )
        }
    }

    fun moveRoomToCategory(
        guild: GuildEntry,
        roomId: RoomId,
        targetCategoryRoomId: RoomId,
        currentCategoryRoomIds: Set<RoomId>,
    ) {
        scope.launchExclusive(_reorderCategoriesInProgress) {
            reportError(null)
            val result = resolveGuildClient(guild)?.let { client ->
                moveRoomToCategory(
                    matrixClient = client,
                    roomId = roomId,
                    targetCategoryRoomId = targetCategoryRoomId,
                    currentCategoryRoomIds = currentCategoryRoomIds,
                )
            } ?: Result.failure(IllegalStateException("Could not resolve active guild context."))
            result.onFailure { throwable ->
                reportError("Could not move room to category: ${throwable.message ?: "Unknown error"}")
            }
        }
    }

    fun acceptGuildInvite(guild: GuildEntry) {
        scope.launchExclusive(_guildInviteActionInProgress) {
            reportError(null)
            val result = resolveGuildClient(guild)?.acceptRoomInvite(roomId = guild.roomId)
                ?: Result.failure(IllegalStateException("No account available for this guild invite."))
            result.fold(
                onSuccess = { roomId -> onRoomSelected(roomId) },
                onFailure = { throwable ->
                    reportError("Could not accept invite: ${throwable.message ?: "Unknown error"}")
                },
            )
        }
    }

    fun declineGuildInvite(guild: GuildEntry) {
        scope.launchExclusive(_guildInviteActionInProgress) {
            reportError(null)
            val result = resolveGuildClient(guild)?.declineRoomInvite(roomId = guild.roomId)
                ?: Result.failure(IllegalStateException("No account available for this guild invite."))
            result.fold(
                onSuccess = { onGuildSelected(null) },
                onFailure = { throwable ->
                    reportError("Could not decline invite: ${throwable.message ?: "Unknown error"}")
                },
            )
        }
    }
}
