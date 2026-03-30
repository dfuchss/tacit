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
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.matrixClients
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.util.ErrorType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry
import org.fuchss.matrix.tacit.viewmodel.util.searchUserDirectory
import org.koin.core.component.get

internal interface TacitRoomListViewModel : RoomListViewModel {
    val selectedGuild: StateFlow<GuildEntry?>
    val mode: StateFlow<RoomListMode>
    val typedElements: StateFlow<List<TacitRoomListElementViewModel>>
    val guilds: StateFlow<List<GuildEntry>>
    val guildAvatars: StateFlow<Map<String, ByteArray?>>
    val visibleRooms: StateFlow<List<TacitRoomListElementViewModel>>
    val guildChannelGroups: StateFlow<List<TacitGuildChannelGroup>>
    val inviteRooms: StateFlow<List<TacitRoomListElementViewModel>>
    val browseChannels: StateFlow<List<SpaceChannelEntry>>
    val dmUnreadCount: StateFlow<Int>
    val guildUnreadCounts: StateFlow<Map<String, Int>>
    val canUseSelectedGuildAccount: StateFlow<Boolean>
    val canUsePreferredCreationAccount: StateFlow<Boolean>
    val createGuildInProgress: StateFlow<Boolean>
    val createChannelInProgress: StateFlow<Boolean>
    val joiningChannelRoomId: StateFlow<RoomId?>
    val inviteToGuildInProgress: StateFlow<Boolean>
    val createDirectMessageInProgress: StateFlow<Boolean>
    val createGroupChannelInProgress: StateFlow<Boolean>
    val guildInviteActionInProgress: StateFlow<Boolean>

    fun selectGuild(guild: GuildEntry?)
    fun reportError(message: String?, errorType: ErrorType = ErrorType.JUST_DISMISS)
    fun createGuild(name: String, topic: String, createDefaultChannel: Boolean)
    fun createChannel(guild: GuildEntry, name: String, topic: String)
    fun joinChannel(guild: GuildEntry, channel: SpaceChannelEntry)
    suspend fun searchUsersForGuild(guild: GuildEntry, query: String): Result<List<UserDirectoryEntry>>
    fun inviteUserToGuild(guild: GuildEntry, userId: String, reason: String)
    suspend fun searchUsersForDirectMessages(query: String): Result<List<UserDirectoryEntry>>
    fun startDirectMessage(userId: String)
    fun createGroupChannel(name: String, topic: String)
    fun acceptGuildInvite(guild: GuildEntry)
    fun declineGuildInvite(guild: GuildEntry)
    fun reorderGuild(fromIndex: Int, toIndex: Int)
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

    private val selectedMatrixClientUserIds: StateFlow<List<UserId>> =
        combine(matrixClients, accountViewModel.activeAccount) {
                clients,
                activeAccount,
            ->
            if (activeAccount == null) clients.keys.toList() else listOfNotNull(activeAccount.takeIf {
                clients.containsKey(
                    it
                )
            })
        }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    private val selectedMatrixClients: Flow<List<MatrixClient>> =
        combine(matrixClients, selectedMatrixClientUserIds) { clients, userIds ->
            userIds.mapNotNull { clients[it] }
        }

    private val selectedGuildUserId: StateFlow<UserId?> =
        combine(selectedGuild, selectedMatrixClientUserIds) { guild, selectedUserIds ->
            guild?.userId?.takeIf { selectedUserIds.contains(it) }
        }.stateIn(coroutineScope, WhileSubscribed(), null)

    private val preferredCreationUserId: StateFlow<UserId?> =
        combine(matrixClients, accountViewModel.activeAccount) { clients, activeAccount ->
            activeAccount?.takeIf { clients.containsKey(it) } ?: clients.keys.firstOrNull()
        }.stateIn(coroutineScope, WhileSubscribed(), null)

    override val canUseSelectedGuildAccount: StateFlow<Boolean> = selectedGuildUserId
        .map { it != null }
        .stateIn(coroutineScope, WhileSubscribed(), false)

    override val canUsePreferredCreationAccount: StateFlow<Boolean> = preferredCreationUserId
        .map { it != null }
        .stateIn(coroutineScope, WhileSubscribed(), false)

    private val settings = get<MatrixMessengerSettingsHolder>()

    private val persistedGuildOrder: StateFlow<List<String>> =
        settings
            .mapLatest { messengerSettings ->
                readTacitGuildOrder(messengerSettings)
            }
            .stateIn(
                coroutineScope,
                WhileSubscribed(),
                readTacitGuildOrder(settings.value)
            )

    private val runtimeGuildOrderOverride = MutableStateFlow<List<String>?>(null)

    private val guildOrder: StateFlow<List<String>> =
        combine(persistedGuildOrder, runtimeGuildOrderOverride) { persistedOrder, runtimeOrder ->
            runtimeOrder ?: persistedOrder
        }.stateIn(coroutineScope, WhileSubscribed(), persistedGuildOrder.value)

    private val discoveredGuilds: StateFlow<List<GuildEntry>> = selectedMatrixClients.flatMapLatest { clients ->
        if (clients.isEmpty()) flowOf(emptyList())
        else combine(clients.map { matrixClient ->
            matrixClient.room.getAll()
                .flattenValues()
                .flatMapLatest { rooms ->
                    val spaces = rooms.filter { room ->
                        room.createEventContent?.type == RoomType.Space &&
                                (room.membership == Membership.JOIN || room.membership == Membership.INVITE)
                    }
                    if (spaces.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        val childSpaceFlows = spaces.map { space ->
                            matrixClient.room.getAllState(space.roomId, ChildEventContent::class)
                                .flattenValues()
                                .map { childState ->
                                    childState.mapNotNull { childEvent ->
                                        val childRoomId = runCatching { RoomId(childEvent.stateKey) }.getOrNull()
                                            ?: return@mapNotNull null
                                        val childRoom = rooms.firstOrNull { it.roomId == childRoomId }
                                        if (childRoom?.createEventContent?.type == RoomType.Space) childRoomId else null
                                    }.toSet()
                                }
                        }
                        combine(childSpaceFlows) { childSets ->
                            childSets
                                .flatMap { it }
                                .toSet()
                        }.map { childSpaceIds ->
                            spaces
                                .filterNot { room -> childSpaceIds.contains(room.roomId) }
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
                    }
                }
        }) { guildsByClient ->
            guildsByClient.toList().flatten().sortedBy { it.displayName ?: it.roomId.full }
        }
    }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val guilds: StateFlow<List<GuildEntry>> =
        combine(discoveredGuilds, guildOrder) { discoveredGuilds, currentOrder ->
            if (discoveredGuilds.isEmpty()) {
                emptyList()
            } else {
                val discoveredByKey = discoveredGuilds.associateBy { it.key() }
                val orderedKeys = currentOrder.filter { discoveredByKey.containsKey(it) }
                val orderedGuilds = orderedKeys.mapNotNull { discoveredByKey[it] }
                val unorderedGuilds = discoveredGuilds
                    .filterNot { guild -> orderedKeys.contains(guild.key()) }
                    .sortedBy { guild -> guild.displayName ?: guild.roomId.full }
                orderedGuilds + unorderedGuilds
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
        combine(selectedGuild, selectedGuildUserId, selectedMatrixClients) { guild, guildUserId, clients ->
            Triple(guild, guildUserId, clients)
        }.flatMapLatest { (guild, guildUserId, clients) ->
            if (guild == null) flowOf(emptyMap())
            else {
                val client = clients.find { it.userId == guildUserId }
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

    private val allGuildChildRoomIds: StateFlow<Set<RoomId>> = allGuildChildrenByGuild
        .map { childrenByGuild ->
            childrenByGuild.values
                .flatten()
                .toSet()
        }
        .stateIn(coroutineScope, WhileSubscribed(), emptySet())

    private val allSpaceChildRoomIds: StateFlow<Set<RoomId>> = selectedMatrixClients.flatMapLatest { clients ->
        if (clients.isEmpty()) flowOf(emptySet())
        else combine(clients.map { matrixClient ->
            matrixClient.room.getAll()
                .flattenValues()
                .flatMapLatest { rooms ->
                    val spaceRoomIds = rooms
                        .filter { room ->
                            room.createEventContent?.type == RoomType.Space &&
                                    (room.membership == Membership.JOIN || room.membership == Membership.INVITE)
                        }
                        .map { it.roomId }
                    if (spaceRoomIds.isEmpty()) {
                        flowOf(emptySet())
                    } else {
                        combine(spaceRoomIds.map { spaceRoomId ->
                            matrixClient.room.getAllState(spaceRoomId, ChildEventContent::class)
                                .flattenValues()
                                .map { childState ->
                                    childState.mapNotNull { childEvent ->
                                        runCatching { RoomId(childEvent.stateKey) }.getOrNull()
                                    }.toSet()
                                }
                        }) { childSets ->
                            childSets.flatMap { it }.toSet()
                        }
                    }
                }
        }) { childIdsByClient ->
            childIdsByClient.toList().flatten().toSet()
        }
    }.stateIn(coroutineScope, WhileSubscribed(), emptySet())

    private val roomDerived: StateFlow<List<TacitRoomDerived>> = typedElements.flatMapLatest { rooms ->
        if (rooms.isEmpty()) flowOf(emptyList())
        else combine(rooms.map { room ->
            combine(
                room.isLoaded,
                room.roomName,
                room.isDirectRoom,
                room.isSpaceRoom,
            ) { isLoaded, roomName, isDirect, isSpace ->
                RoomDerivedIdentityState(
                    isLoaded = isLoaded,
                    roomName = roomName,
                    isDirect = isDirect,
                    isSpace = isSpace,
                )
            }.flatMapLatest { identity ->
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
                    val isJoined = identity.isLoaded && isMembershipKnown && !inviteState && !knockState && !leaveState
                    TacitRoomDerived(
                        room = room,
                        roomId = room.roomId,
                        roomName = identity.roomName,
                        isDirect = identity.isDirect,
                        isSpace = identity.isSpace,
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

    private val selectedGuildClient: Flow<MatrixClient?> =
        combine(
            selectedGuildUserId,
            matrixClients,
            selectedMatrixClientUserIds
        ) { guildUserId, clients, selectedUserIds ->
            guildUserId
                ?.takeIf { selectedUserIds.contains(it) }
                ?.let { clients[it] }
        }

    private val unknownDisplayNames: StateFlow<Map<RoomId, String>> =
        combine(selectedGuild, selectedGuildClient, guildChildren, roomDerived) { guild, client, children, rooms ->
            TacitUnknownDisplayNamesContext(guild, client, children, rooms)
        }.mapLatest { context ->
            val guild = context.guild ?: return@mapLatest emptyMap()
            val client = context.client ?: return@mapLatest emptyMap()
            val children = context.children
            val rooms = context.rooms
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

    private val guildCategoryChildren: StateFlow<Map<RoomId, Set<RoomId>>> =
        combine(selectedGuildClient, guildChildren) { client, children ->
            client to children.keys.toList()
        }.flatMapLatest { (client, categoryRoomIds) ->
            if (client == null || categoryRoomIds.isEmpty()) flowOf(emptyMap())
            else {
                val categoryFlows = categoryRoomIds.map { categoryRoomId ->
                    client.room.getAllState(categoryRoomId, ChildEventContent::class)
                        .flattenValues()
                        .map { childState ->
                            val children = childState
                                .mapNotNull { childEvent -> runCatching { RoomId(childEvent.stateKey) }.getOrNull() }
                                .toSet()
                            categoryRoomId to children
                        }
                }
                combine(categoryFlows) { categories -> categories.toMap() }
            }
        }.stateIn(coroutineScope, WhileSubscribed(), emptyMap())

    override val visibleRooms: StateFlow<List<TacitRoomListElementViewModel>> =
        combine(roomDerived, mode, guildChildren, guildCategoryChildren, allSpaceChildRoomIds) {
                rooms,
                mode,
                children,
                categoryChildren,
                allSpaceChildIds,
            ->
            when (mode) {
                RoomListMode.DirectMessages -> rooms.filter { room ->
                    room.isJoined && (room.isDirect || isDmGroupRoom(room, allSpaceChildIds))
                }

                is RoomListMode.GuildChannels -> {
                    val nestedCategoryChildRoomIds = categoryChildren.values.flatten().toSet()
                    rooms.filter {
                        it.isJoined &&
                                !it.isDirect &&
                                !it.isSpace &&
                                (children.containsKey(it.roomId) || nestedCategoryChildRoomIds.contains(it.roomId))
                    }
                }
            }.map { it.room }
        }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val guildChannelGroups: StateFlow<List<TacitGuildChannelGroup>> =
        combine(
            mode,
            roomDerived,
            visibleRooms,
            combine(guildChildren, guildCategoryChildren, unknownDisplayNames) { children, categoryChildren, unknownNames ->
                Triple(children, categoryChildren, unknownNames)
            },
        ) {
                mode,
                rooms,
                visibleRooms,
                hierarchy,
            ->
            val (children, categoryChildren, unknownNames) = hierarchy
            if (mode !is RoomListMode.GuildChannels) {
                emptyList()
            } else {
                val roomById = rooms.associateBy { it.roomId }
                val visibleById = visibleRooms.associateBy { it.roomId }
                val visibleOrder = visibleRooms.mapIndexed { index, room -> room.roomId to index }.toMap()

                val categories = children.keys
                    .filter { categoryChildren.containsKey(it) && roomById[it]?.isSpace != false }
                    .sortedBy { roomById[it]?.roomName?.lowercase() ?: unknownNames[it]?.lowercase() ?: it.full }

                categories.mapNotNull { categoryRoomId ->
                    val channels = categoryChildren[categoryRoomId]
                        .orEmpty()
                        .mapNotNull { roomId ->
                            val derived = roomById[roomId] ?: return@mapNotNull null
                            if (!derived.isJoined || derived.isDirect || derived.isSpace) return@mapNotNull null
                            visibleById[roomId]
                        }
                        .sortedBy { visibleOrder[it.roomId] ?: Int.MAX_VALUE }
                    if (channels.isEmpty()) null
                    else TacitGuildChannelGroup(
                        categoryRoomId = categoryRoomId,
                        categoryName = roomById[categoryRoomId]?.roomName ?: unknownNames[categoryRoomId] ?: categoryRoomId.full,
                        channels = channels,
                    )
                }
            }
        }.stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val inviteRooms: StateFlow<List<TacitRoomListElementViewModel>> =
        combine(roomDerived, mode, guildChildren, allSpaceChildRoomIds) { rooms, mode, children, allSpaceChildIds ->
            when (mode) {
                RoomListMode.DirectMessages -> rooms.filter { room ->
                    room.isInvite && (room.isDirect || isDmGroupRoom(room, allSpaceChildIds))
                }

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

    override val dmUnreadCount: StateFlow<Int> = combine(roomDerived, allSpaceChildRoomIds) { rooms, allSpaceChildIds ->
        rooms.count { room ->
            room.isUnread && room.isJoined && (room.isDirect || isDmGroupRoom(room, allSpaceChildIds))
        }
    }
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

    override fun selectGuild(guild: GuildEntry?) {
        _selectedGuild.update { guild }
    }

    private fun resolveGuildClient(guild: GuildEntry): MatrixClient? {
        if (!selectedMatrixClientUserIds.value.contains(guild.userId)) return null
        return matrixClients.value[guild.userId]
    }

    private fun resolvePreferredCreationClient(): MatrixClient? =
        preferredCreationUserId.value?.let { matrixClients.value[it] }

    private val actions = TacitRoomListActions(
        scope = coroutineScope,
        reportError = { message -> reportError(message) },
        onGuildSelected = ::selectGuild,
        onRoomSelected = delegate::selectRoom,
        resolveGuildClient = ::resolveGuildClient,
        resolvePreferredCreationClient = ::resolvePreferredCreationClient,
    )

    override val createGuildInProgress: StateFlow<Boolean> = actions.createGuildInProgress
    override val createChannelInProgress: StateFlow<Boolean> = actions.createChannelInProgress
    override val joiningChannelRoomId: StateFlow<RoomId?> = actions.joiningChannelRoomId
    override val inviteToGuildInProgress: StateFlow<Boolean> = actions.inviteToGuildInProgress
    override val createDirectMessageInProgress: StateFlow<Boolean> = actions.createDirectMessageInProgress
    override val createGroupChannelInProgress: StateFlow<Boolean> = actions.createGroupChannelInProgress
    override val guildInviteActionInProgress: StateFlow<Boolean> = actions.guildInviteActionInProgress

    override fun createGuild(name: String, topic: String, createDefaultChannel: Boolean) =
        actions.createGuild(name, topic, createDefaultChannel)

    override fun createChannel(guild: GuildEntry, name: String, topic: String) =
        actions.createChannel(guild, name, topic)

    override fun joinChannel(guild: GuildEntry, channel: SpaceChannelEntry) =
        actions.joinChannel(guild, channel)

    override suspend fun searchUsersForGuild(guild: GuildEntry, query: String): Result<List<UserDirectoryEntry>> {
        val client = resolveGuildClient(guild)
            ?: return Result.failure(IllegalStateException("No account available for this guild."))
        return client.searchUserDirectory(query)
    }

    override fun inviteUserToGuild(guild: GuildEntry, userId: String, reason: String) =
        actions.inviteUserToGuild(guild, userId, reason)

    override suspend fun searchUsersForDirectMessages(query: String): Result<List<UserDirectoryEntry>> {
        val client = resolvePreferredCreationClient()
            ?: return Result.failure(IllegalStateException("No active Matrix account available."))
        return client.searchUserDirectory(query)
    }

    override fun startDirectMessage(userId: String) = actions.startDirectMessage(userId)

    override fun createGroupChannel(name: String, topic: String) =
        actions.createGroupChannel(name, topic)

    override fun acceptGuildInvite(guild: GuildEntry) = actions.acceptGuildInvite(guild)

    override fun declineGuildInvite(guild: GuildEntry) = actions.declineGuildInvite(guild)

    override fun reorderGuild(fromIndex: Int, toIndex: Int) {
        val current = guilds.value
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val reordered = current.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        val reorderedKeys = reordered.map { it.key() }
        runtimeGuildOrderOverride.value = reorderedKeys
        coroutineScope.launch {
            settings.update {
                writeTacitGuildOrder(reorderedKeys)
            }
        }
    }

    private fun isDmGroupRoom(
        room: TacitRoomDerived,
        allSpaceChildRoomIds: Set<RoomId>,
    ): Boolean = !room.isDirect && !room.isSpace && !allSpaceChildRoomIds.contains(room.roomId)
}

private data class RoomDerivedIdentityState(
    val isLoaded: Boolean,
    val roomName: String?,
    val isDirect: Boolean,
    val isSpace: Boolean,
)

internal data class TacitGuildChannelGroup(
    val categoryRoomId: RoomId,
    val categoryName: String,
    val channels: List<TacitRoomListElementViewModel>,
)
