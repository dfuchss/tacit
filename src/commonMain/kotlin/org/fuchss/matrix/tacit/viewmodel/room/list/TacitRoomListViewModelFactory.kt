package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.matrixClients
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.util.ErrorType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry
import org.fuchss.matrix.tacit.viewmodel.util.acceptRoomInvite
import org.fuchss.matrix.tacit.viewmodel.util.declineRoomInvite

internal interface TacitRoomListViewModel : RoomListViewModel {
    val selectedGuild: StateFlow<GuildEntry?>
    val mode: StateFlow<RoomListMode>
    val typedElements: StateFlow<List<TacitRoomListElementViewModel>>
    val guilds: StateFlow<List<GuildEntry>>
    val guildAvatars: StateFlow<Map<String, ByteArray?>>
    val visibleRooms: StateFlow<List<TacitRoomListElementViewModel>>
    val inviteRooms: StateFlow<List<TacitRoomListElementViewModel>>
    val browseChannels: StateFlow<List<SpaceChannelEntry>>
    val dmUnreadCount: StateFlow<Int>
    val guildUnreadCounts: StateFlow<Map<String, Int>>
    val selectedGuildInviteFallback: StateFlow<GuildEntry?>
    val selectedGuildClient: StateFlow<MatrixClient?>
    val preferredCreationClient: StateFlow<MatrixClient?>

    fun selectGuild(guild: GuildEntry?)
    fun reportError(message: String?, errorType: ErrorType = ErrorType.JUST_DISMISS)
    suspend fun acceptGuildInvite(guild: GuildEntry): Result<RoomId>
    suspend fun declineGuildInvite(guild: GuildEntry): Result<Unit>
}

internal object TacitRoomListViewModelFactory : RoomListViewModelFactory {
    override fun create(
        viewModelContext: ViewModelContext,
        selectedRoomId: StateFlow<RoomId?>,
        onRoomSelected: (UserId, RoomId) -> Unit,
        onStartCreateNewRoom: (UserId) -> Unit,
        onUserSettingsSelected: () -> Unit,
        onShowAccounts: () -> Unit,
        onOpenAppInfo: () -> Unit,
        onSendLogs: () -> Unit,
        onAccountSelected: () -> Unit,
        onStartVerification: (UserId) -> Unit,
        onCloseRoom: () -> Unit,
    ): RoomListViewModel {
        val delegate = RoomListViewModelFactory.create(
            viewModelContext = viewModelContext,
            selectedRoomId = selectedRoomId,
            onRoomSelected = onRoomSelected,
            onStartCreateNewRoom = onStartCreateNewRoom,
            onUserSettingsSelected = onUserSettingsSelected,
            onShowAccounts = onShowAccounts,
            onOpenAppInfo = onOpenAppInfo,
            onSendLogs = onSendLogs,
            onAccountSelected = onAccountSelected,
            onStartVerification = onStartVerification,
            onCloseRoom = onCloseRoom,
        )
        return TacitRoomListViewModelImpl(delegate, viewModelContext)
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private class TacitRoomListViewModelImpl(
    private val delegate: RoomListViewModel,
    viewModelContext: ViewModelContext,
) : TacitRoomListViewModel, RoomListViewModel by delegate, ViewModelContext by viewModelContext {

    private data class RoomDerived(
        val room: TacitRoomListElementViewModel,
        val roomId: RoomId,
        val roomName: String?,
        val isDirect: Boolean,
        val isInvite: Boolean,
        val isKnock: Boolean,
        val isLeave: Boolean,
        val isJoined: Boolean,
        val isUnread: Boolean,
    )

    private data class UnknownDisplayNamesInput(
        val guild: GuildEntry?,
        val client: MatrixClient?,
        val children: Map<RoomId, Set<String>>,
        val rooms: List<RoomDerived>,
    )

    private val _selectedGuild = MutableStateFlow<GuildEntry?>(null)
    override val selectedGuild: StateFlow<GuildEntry?> = _selectedGuild

    private val tacitError = MutableStateFlow<String?>(null)
    private val tacitErrorType = MutableStateFlow(ErrorType.JUST_DISMISS)
    override val error: StateFlow<String?> = combine(delegate.error, tacitError) { delegateError, localError ->
        localError ?: delegateError
    }.stateIn(coroutineScope, WhileSubscribed(), null)
    override val errorType: StateFlow<ErrorType> =
        combine(
            delegate.error,
            delegate.errorType,
            tacitError,
            tacitErrorType
        ) { delegateError, delegateType, localError, localType ->
            when {
                localError != null -> localType
                delegateError != null -> delegateType
                else -> ErrorType.JUST_DISMISS
            }
        }.stateIn(coroutineScope, WhileSubscribed(), ErrorType.JUST_DISMISS)

    override fun errorDismiss() {
        tacitError.value = null
        delegate.errorDismiss()
    }

    override fun reportError(message: String?, errorType: ErrorType) {
        tacitErrorType.value = errorType
        tacitError.value = message
    }

    override val mode: StateFlow<RoomListMode> = selectedGuild
        .map { guild -> guild?.let { RoomListMode.GuildChannels(it) } ?: RoomListMode.DirectMessages }
        .stateIn(coroutineScope, WhileSubscribed(), RoomListMode.DirectMessages)

    override val typedElements: StateFlow<List<TacitRoomListElementViewModel>> = delegate.elements
        .map { elements ->
            elements.map { element ->
                element as? TacitRoomListElementViewModel
                    ?: error("Expected TacitRoomListElementViewModel for room ${element.roomId.full}")
            }
        }
        .stateIn(coroutineScope, WhileSubscribed(), emptyList())

    private val selectedMatrixClients: StateFlow<List<MatrixClient>> =
        combine(matrixClients, accountViewModel.activeAccount) {
                clients,
                activeAccount,
            ->
            if (activeAccount == null) clients.values.toList() else listOfNotNull(clients[activeAccount])
        }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val selectedGuildClient: StateFlow<MatrixClient?> =
        combine(selectedGuild, selectedMatrixClients) { guild, clients ->
            guild?.let { selected -> clients.find { it.userId == selected.userId } }
        }.stateIn(coroutineScope, WhileSubscribed(), null)

    override val preferredCreationClient: StateFlow<MatrixClient?> =
        combine(matrixClients, accountViewModel.activeAccount) { clients, activeAccount ->
            activeAccount?.let { clients[it] } ?: clients.values.firstOrNull()
        }.stateIn(coroutineScope, WhileSubscribed(), null)

    override val guilds: StateFlow<List<GuildEntry>> = selectedMatrixClients.flatMapLatest { clients ->
        if (clients.isEmpty()) flowOf(emptyList())
        else combine(clients.map { matrixClient ->
            matrixClient.room.getAll()
                .flattenValues()
                .map { rooms ->
                    rooms
                        .filter { room ->
                            room.createEventContent?.type == RoomType.Space &&
                                    (room.membership == Membership.JOIN || room.membership == Membership.INVITE)
                        }
                        .map { room ->
                            GuildEntry(
                                roomId = room.roomId,
                                userId = matrixClient.userId,
                                displayName = room.name?.explicitName,
                                avatarUri = room.avatarUrl?.ifBlank { null },
                                membership = room.membership,
                            )
                        }
                }
        }) { guildsByClient ->
            guildsByClient.toList().flatten().sortedBy { it.displayName ?: it.roomId.full }
        }
    }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val guildAvatars: StateFlow<Map<String, ByteArray?>> =
        combine(guilds, selectedMatrixClients) { allGuilds, clients -> allGuilds to clients }
            .mapLatest { (allGuilds, clients) ->
                if (allGuilds.isEmpty() || clients.isEmpty()) return@mapLatest emptyMap()
                val avatarCache = mutableMapOf<String, ByteArray?>()
                buildMap {
                    allGuilds.forEach { guild ->
                        val avatarUri = guild.avatarUri
                        if (avatarUri == null) {
                            put(guild.key(), null)
                            return@forEach
                        }
                        val matrixClient = clients.find { it.userId == guild.userId }
                        if (matrixClient == null) {
                            put(guild.key(), null)
                            return@forEach
                        }
                        val avatar = avatarCache.getOrPut(avatarUri) {
                            matrixClient.media
                                .getThumbnail(uri = avatarUri, width = 46L, height = 46L)
                                .getOrNull()
                                ?.toByteArray(maxSize = 512L * 1024L)
                        }
                        put(guild.key(), avatar)
                    }
                }
            }.stateIn(coroutineScope, WhileSubscribed(), emptyMap())

    private val guildChildren: StateFlow<Map<RoomId, Set<String>>> =
        combine(selectedGuild, selectedMatrixClients) { guild, clients ->
            guild to clients
        }.flatMapLatest { (guild, clients) ->
            if (guild == null) flowOf(emptyMap())
            else {
                val client = clients.find { it.userId == guild.userId }
                if (client == null) flowOf(emptyMap())
                else client.room.getAllState(guild.roomId, ChildEventContent::class)
                    .flattenValues()
                    .map { childState ->
                        childState.mapNotNull { childEvent ->
                            val childRoomId =
                                runCatching { RoomId(childEvent.stateKey) }.getOrNull() ?: return@mapNotNull null
                            childRoomId to childEvent.content.via
                        }.toMap()
                    }
            }
        }.stateIn(coroutineScope, WhileSubscribed(), emptyMap())

    private val allGuildChildrenByGuild: StateFlow<Map<String, Set<RoomId>>> =
        combine(guilds, selectedMatrixClients) { guilds, clients ->
            guilds to clients
        }.flatMapLatest { (guilds, clients) ->
            if (guilds.isEmpty() || clients.isEmpty()) flowOf(emptyMap())
            else {
                val childFlows = guilds.mapNotNull { guild ->
                    val client = clients.find { it.userId == guild.userId } ?: return@mapNotNull null
                    client.room.getAllState(guild.roomId, ChildEventContent::class)
                        .flattenValues()
                        .map { childState ->
                            guild.key() to childState.mapNotNull { childEvent -> runCatching { RoomId(childEvent.stateKey) }.getOrNull() }
                                .toSet()
                        }
                }
                if (childFlows.isEmpty()) flowOf(emptyMap())
                else combine(childFlows) { children -> children.toMap() }
            }
        }.stateIn(coroutineScope, WhileSubscribed(), emptyMap())

    private val roomDerived: StateFlow<List<RoomDerived>> = typedElements.flatMapLatest { rooms ->
        if (rooms.isEmpty()) flowOf(emptyList())
        else combine(rooms.map { room ->
            combine(
                room.isLoaded,
                room.roomName,
                room.isDirectRoom,
            ) { isLoaded, roomName, isDirect ->
                Triple(isLoaded, roomName, isDirect)
            }.flatMapLatest { (isLoaded, roomName, isDirect) ->
                combine(
                    room.isInvite,
                    room.isKnock,
                    room.isLeave,
                    room.isUnread
                ) { isInvite, isKnock, isLeave, isUnread ->
                    val inviteState = isInvite == true
                    val knockState = isKnock == true
                    val leaveState = isLeave == true
                    val isMembershipKnown = isInvite != null && isKnock != null && isLeave != null
                    val isJoined = isLoaded && isMembershipKnown && !inviteState && !knockState && !leaveState
                    RoomDerived(
                        room = room,
                        roomId = room.roomId,
                        roomName = roomName,
                        isDirect = isDirect,
                        isInvite = inviteState,
                        isKnock = knockState,
                        isLeave = leaveState,
                        isJoined = isJoined,
                        isUnread = isUnread == true,
                    )
                }
            }
        }) { it.toList() }
    }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    private val unknownDisplayNames: StateFlow<Map<RoomId, String>> =
        combine(selectedGuild, selectedGuildClient, guildChildren, roomDerived) { guild, client, children, rooms ->
            UnknownDisplayNamesInput(guild = guild, client = client, children = children, rooms = rooms)
        }.mapLatest { (guild, client, children, rooms) ->
            if (guild == null || client == null) return@mapLatest emptyMap()
            val knownRoomIds = rooms.map { it.roomId }.toSet()
            val unknownRoomIds = children.keys.filterNot { knownRoomIds.contains(it) }.toSet()
            if (unknownRoomIds.isEmpty()) return@mapLatest emptyMap()

            val hierarchy = client.api.room.getHierarchy(roomId = guild.roomId, limit = 100).getOrNull()
                ?: return@mapLatest emptyMap()
            val resolvedNames = mutableMapOf<RoomId, String>()
            unknownRoomIds.forEach { roomId ->
                val childInfo = hierarchy.rooms.firstOrNull { roomInfo -> roomInfo.roomId == roomId }
                val displayName = childInfo?.name?.takeIf { it.isNotBlank() }
                if (displayName != null) resolvedNames[roomId] = displayName
            }
            resolvedNames
        }.stateIn(coroutineScope, WhileSubscribed(), emptyMap())

    override val visibleRooms: StateFlow<List<TacitRoomListElementViewModel>> =
        combine(roomDerived, mode, guildChildren) { rooms, mode, children ->
            when (mode) {
                RoomListMode.DirectMessages -> rooms.filter { it.isJoined && it.isDirect }
                is RoomListMode.GuildChannels -> rooms.filter { it.isJoined && !it.isDirect && children.containsKey(it.roomId) }
            }.map { it.room }
        }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val inviteRooms: StateFlow<List<TacitRoomListElementViewModel>> =
        combine(roomDerived, mode, guildChildren) { rooms, mode, children ->
            when (mode) {
                RoomListMode.DirectMessages -> rooms.filter { it.isInvite }
                is RoomListMode.GuildChannels -> rooms.filter {
                    it.isInvite && !it.isDirect && (it.roomId == mode.guild.roomId || children.containsKey(it.roomId))
                }
            }.map { it.room }
        }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val browseChannels: StateFlow<List<SpaceChannelEntry>> =
        combine(roomDerived, mode, guildChildren, unknownDisplayNames) {
                rooms,
                mode,
                children,
                unknownNames,
            ->
            when (mode) {
                RoomListMode.DirectMessages -> emptyList()
                is RoomListMode.GuildChannels -> {
                    val knownChannelsById = rooms
                        .filter { !it.isDirect && children.containsKey(it.roomId) }
                        .associateBy { it.roomId }

                    val known = knownChannelsById.values.map { entry ->
                        val isJoinable = !entry.isJoined && !entry.isInvite && !entry.isKnock && !entry.isLeave
                        SpaceChannelEntry(
                            roomId = entry.roomId,
                            displayName = entry.roomName ?: entry.roomId.full,
                            status = when {
                                entry.isJoined -> "Joined"
                                entry.isInvite -> "Invited"
                                entry.isKnock -> "Knocking"
                                entry.isLeave -> "Left"
                                else -> "Not joined"
                            },
                            isJoined = entry.isJoined,
                            isJoinable = isJoinable,
                            via = children[entry.roomId].orEmpty(),
                        )
                    }

                    val unknown = children.keys
                        .filterNot { knownChannelsById.containsKey(it) }
                        .map { roomId ->
                            val resolvedName = unknownNames[roomId]
                            val isJoinable = resolvedName != null
                            SpaceChannelEntry(
                                roomId = roomId,
                                displayName = resolvedName ?: roomId.full,
                                status = if (isJoinable) "Not joined" else "Unknown",
                                isJoined = false,
                                isJoinable = isJoinable,
                                via = children[roomId].orEmpty(),
                            )
                        }

                    (known + unknown)
                        .filter { it.isJoined || it.isJoinable }
                        .sortedBy { it.displayName.lowercase() }
                }
            }
        }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val dmUnreadCount: StateFlow<Int> = roomDerived
        .map { rooms -> rooms.count { it.isJoined && it.isUnread } }
        .stateIn(coroutineScope, WhileSubscribed(), 0)

    override val guildUnreadCounts: StateFlow<Map<String, Int>> =
        combine(roomDerived, guilds, allGuildChildrenByGuild) { rooms, guilds, allChildren ->
            val unreadChannelRoomIds = rooms
                .filter { !it.isDirect && it.isJoined && it.isUnread }
                .map { it.roomId }
                .toSet()
            guilds.associate { guild ->
                val count = allChildren[guild.key()].orEmpty().count { unreadChannelRoomIds.contains(it) }
                guild.key() to count
            }
        }.stateIn(coroutineScope, WhileSubscribed(), emptyMap())

    override val selectedGuildInviteFallback: StateFlow<GuildEntry?> = combine(mode, inviteRooms) { mode, invites ->
        val selected = (mode as? RoomListMode.GuildChannels)?.guild
        if (selected != null && selected.isInvite && invites.none { it.roomId == selected.roomId }) selected else null
    }.stateIn(coroutineScope, WhileSubscribed(), null)

    override fun selectGuild(guild: GuildEntry?) {
        _selectedGuild.update { guild }
    }

    override suspend fun acceptGuildInvite(guild: GuildEntry): Result<RoomId> {
        val client = selectedMatrixClients.value.find { it.userId == guild.userId }
            ?: return Result.failure(IllegalStateException("No account available for this guild invite."))
        return client.acceptRoomInvite(roomId = guild.roomId)
    }

    override suspend fun declineGuildInvite(guild: GuildEntry): Result<Unit> {
        val client = selectedMatrixClients.value.find { it.userId == guild.userId }
            ?: return Result.failure(IllegalStateException("No account available for this guild invite."))
        return client.declineRoomInvite(roomId = guild.roomId)
    }
}
