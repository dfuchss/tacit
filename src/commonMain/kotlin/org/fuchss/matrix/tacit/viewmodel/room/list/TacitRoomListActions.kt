package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

    private val _guildInviteActionInProgress = MutableStateFlow(false)
    val guildInviteActionInProgress: StateFlow<Boolean> = _guildInviteActionInProgress

    fun createGuild(name: String, topic: String, createDefaultChannel: Boolean) {
        if (_createGuildInProgress.value) return
        scope.launch {
            _createGuildInProgress.value = true
            try {
                reportError(null)
                val result = resolvePreferredCreationClient()
                    ?.createGuild(
                        guildName = name,
                        guildTopic = topic,
                        createDefaultChannel = createDefaultChannel,
                    )
                    ?: Result.failure(IllegalStateException("No active Matrix account available for guild creation."))
                result.fold(
                    onSuccess = { guild -> onGuildSelected(guild) },
                    onFailure = { throwable ->
                        reportError("Could not create guild: ${throwable.message ?: "Unknown error"}")
                    },
                )
            } finally {
                _createGuildInProgress.value = false
            }
        }
    }

    fun createChannel(guild: GuildEntry, name: String, topic: String) {
        if (_createChannelInProgress.value) return
        scope.launch {
            _createChannelInProgress.value = true
            try {
                reportError(null)
                val result = resolveGuildClient(guild)?.let { client ->
                    guild.createChannel(
                        matrixClient = client,
                        channelName = name,
                        channelTopic = topic,
                    )
                } ?: Result.failure(IllegalStateException("Could not resolve active guild context."))
                result.fold(
                    onSuccess = { roomId -> onRoomSelected(roomId) },
                    onFailure = { throwable ->
                        reportError("Could not create room: ${throwable.message ?: "Unknown error"}")
                    },
                )
            } finally {
                _createChannelInProgress.value = false
            }
        }
    }

    fun joinChannel(guild: GuildEntry, channel: SpaceChannelEntry) {
        if (_joiningChannelRoomId.value != null || !channel.isJoinable || channel.isJoined) return
        scope.launch {
            _joiningChannelRoomId.value = channel.roomId
            try {
                reportError(null)
                val result = resolveGuildClient(guild)?.joinRoom(
                    roomId = channel.roomId,
                    viaServers = channel.via,
                ) ?: Result.failure(IllegalStateException("No account available for this guild."))
                result.fold(
                    onSuccess = { roomId -> onRoomSelected(roomId) },
                    onFailure = { throwable ->
                        reportError("Could not join room: ${throwable.message ?: "Unknown error"}")
                    },
                )
            } finally {
                _joiningChannelRoomId.value = null
            }
        }
    }

    fun inviteUserToGuild(guild: GuildEntry, userId: String, reason: String) {
        if (_inviteToGuildInProgress.value) return
        scope.launch {
            _inviteToGuildInProgress.value = true
            try {
                reportError(null)
                val result = resolveGuildClient(guild)?.let { client ->
                    guild.inviteMember(
                        matrixClient = client,
                        userId = userId,
                        reason = reason,
                    )
                } ?: Result.failure(IllegalStateException("Could not resolve active guild context."))
                result.onFailure { throwable ->
                    reportError("Could not send invite: ${throwable.message ?: "Unknown error"}")
                }
            } finally {
                _inviteToGuildInProgress.value = false
            }
        }
    }

    fun startDirectMessage(userId: String) {
        if (_createDirectMessageInProgress.value) return
        scope.launch {
            _createDirectMessageInProgress.value = true
            try {
                reportError(null)
                val result = when {
                    !UserId.isValid(userId) -> Result.failure(IllegalArgumentException("No valid userId provided: '$userId'"))
                    else -> resolvePreferredCreationClient()?.let { client ->
                        UserId(userId).findOrCreateDM(client)
                    } ?: Result.failure(IllegalStateException("No active Matrix account available."))
                }
                result.fold(
                    onSuccess = { roomId -> onRoomSelected(roomId) },
                    onFailure = { throwable ->
                        reportError("Could not start direct message: ${throwable.message ?: "Unknown error"}")
                    },
                )
            } finally {
                _createDirectMessageInProgress.value = false
            }
        }
    }

    fun createGroupChannel(name: String, topic: String) {
        if (_createGroupChannelInProgress.value) return
        scope.launch {
            _createGroupChannelInProgress.value = true
            try {
                reportError(null)
                val result = resolvePreferredCreationClient()?.createGroupChannel(
                    roomName = name,
                    roomTopic = topic,
                ) ?: Result.failure(IllegalStateException("No active Matrix account available."))
                result.fold(
                    onSuccess = { roomId -> onRoomSelected(roomId) },
                    onFailure = { throwable ->
                        reportError("Could not create group chat: ${throwable.message ?: "Unknown error"}")
                    },
                )
            } finally {
                _createGroupChannelInProgress.value = false
            }
        }
    }

    fun acceptGuildInvite(guild: GuildEntry) {
        if (_guildInviteActionInProgress.value) return
        scope.launch {
            _guildInviteActionInProgress.value = true
            try {
                reportError(null)
                val result = resolveGuildClient(guild)?.acceptRoomInvite(roomId = guild.roomId)
                    ?: Result.failure(IllegalStateException("No account available for this guild invite."))
                result.fold(
                    onSuccess = { roomId -> onRoomSelected(roomId) },
                    onFailure = { throwable ->
                        reportError("Could not accept invite: ${throwable.message ?: "Unknown error"}")
                    },
                )
            } finally {
                _guildInviteActionInProgress.value = false
            }
        }
    }

    fun declineGuildInvite(guild: GuildEntry) {
        if (_guildInviteActionInProgress.value) return
        scope.launch {
            _guildInviteActionInProgress.value = true
            try {
                reportError(null)
                val result = resolveGuildClient(guild)?.declineRoomInvite(roomId = guild.roomId)
                    ?: Result.failure(IllegalStateException("No account available for this guild invite."))
                result.fold(
                    onSuccess = { onGuildSelected(null) },
                    onFailure = { throwable ->
                        reportError("Could not decline invite: ${throwable.message ?: "Unknown error"}")
                    },
                )
            } finally {
                _guildInviteActionInProgress.value = false
            }
        }
    }
}
