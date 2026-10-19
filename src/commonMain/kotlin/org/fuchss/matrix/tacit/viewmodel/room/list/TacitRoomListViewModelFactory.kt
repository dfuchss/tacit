package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.matrixClients
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.util.ErrorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.fuchss.matrix.tacit.viewmodel.util.tacitWhileSubscribed
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.room.TacitRoomSettingsMode
import org.fuchss.matrix.tacit.viewmodel.room.TacitSpaceSettingsRequests
import org.fuchss.matrix.tacit.viewmodel.room.list.dialogs.*
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.space.TacitGuildLayout
import org.fuchss.matrix.tacit.viewmodel.space.TacitSpaceGraph
import org.fuchss.matrix.tacit.viewmodel.space.TacitSpaceHierarchy
import org.fuchss.matrix.tacit.viewmodel.space.descendantsOf
import org.fuchss.matrix.tacit.viewmodel.space.topLevelGuilds
import org.fuchss.matrix.tacit.viewmodel.util.loadAvatarThumbnail
import org.fuchss.matrix.tacit.viewmodel.util.mapConcurrently
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
    val dmUnreadCount: StateFlow<Int>
    val guildUnreadCounts: StateFlow<Map<String, Int>>
    val canUseSelectedGuildAccount: StateFlow<Boolean>
    val canUsePreferredCreationAccount: StateFlow<Boolean>
    val reorderCategoriesInProgress: StateFlow<Boolean>
    val guildInviteActionInProgress: StateFlow<Boolean>

    val createGuildViewModel: TacitCreateGuildViewModel
    val createChannelViewModel: TacitCreateChannelViewModel
    val createCategoryViewModel: TacitCreateCategoryViewModel
    val createGroupChannelViewModel: TacitCreateGroupChannelViewModel
    val createDirectMessageViewModel: TacitCreateDirectMessageViewModel
    val inviteToGuildViewModel: TacitInviteToGuildViewModel
    val browseChannelsViewModel: TacitBrowseChannelsViewModel

    fun selectGuild(guild: GuildEntry?)
    fun reportError(message: String?, errorType: ErrorType = ErrorType.JUST_DISMISS)
    fun reorderCategories(guild: GuildEntry, orderedCategoryRoomIds: List<RoomId>)
    fun moveRoomToCategory(guild: GuildEntry, roomId: RoomId, targetCategoryRoomId: RoomId)
    fun acceptGuildInvite(guild: GuildEntry)
    fun declineGuildInvite(guild: GuildEntry)
    fun reorderGuild(fromIndex: Int, toIndex: Int)

    /** Opens [spaceRoomId] (a guild or category) with its settings shown in [TacitRoomSettingsMode.SPACE]. */
    fun openSpaceSettings(spaceRoomId: RoomId)
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

private data class GuildAvatarInput(val key: String, val client: MatrixClient?, val avatarUri: String?)

/**
 * Tacit's room list on top of upstream's: splits the rooms into the DM home and guilds (read through
 * [TacitSpaceHierarchy]) and wires one child view model per dialog. The room state is reduced by the pure functions
 * in `TacitRoomListModel.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class TacitRoomListViewModelImpl(
    private val delegate: RoomListViewModel,
    viewModelContext: ViewModelContext,
) : TacitRoomListViewModel, RoomListViewModel by delegate, ViewModelContext by viewModelContext {

    private val tacitI18n = get<TacitI18n>()
    private val settings = get<MatrixMessengerSettingsHolder>()
    private val spaceHierarchy = get<TacitSpaceHierarchy>()
    private val spaceSettingsRequests = get<TacitSpaceSettingsRequests>()

    private fun <T> Flow<T>.stateInScope(initialValue: T): StateFlow<T> =
        stateIn(coroutineScope, tacitWhileSubscribed, initialValue)

    // ---- accounts and selection ----

    private val _selectedGuild = MutableStateFlow<GuildEntry?>(null)
    override val selectedGuild: StateFlow<GuildEntry?> = _selectedGuild

    override fun selectGuild(guild: GuildEntry?) {
        _selectedGuild.update { guild }
    }

    override val mode: StateFlow<RoomListMode> = selectedGuild
        .map { guild -> guild?.let { RoomListMode.GuildChannels(it) } ?: RoomListMode.DirectMessages }
        .stateInScope(RoomListMode.DirectMessages)

    private val selectedMatrixClientUserIds: StateFlow<List<UserId>> =
        combine(matrixClients, accountViewModel.activeAccount) { clients, activeAccount ->
            if (activeAccount == null) clients.keys.toList()
            else listOfNotNull(activeAccount.takeIf { clients.containsKey(it) })
        }.stateInScope(emptyList())

    private val selectedMatrixClients: Flow<List<MatrixClient>> =
        combine(matrixClients, selectedMatrixClientUserIds) { clients, userIds -> userIds.mapNotNull { clients[it] } }
            .distinctUntilChanged()

    private val selectedGuildUserId: StateFlow<UserId?> =
        combine(selectedGuild, selectedMatrixClientUserIds) { guild, selectedUserIds ->
            guild?.userId?.takeIf { selectedUserIds.contains(it) }
        }.stateInScope(null)

    private val selectedGuildClient: Flow<MatrixClient?> =
        combine(selectedGuildUserId, matrixClients) { guildUserId, clients -> guildUserId?.let { clients[it] } }

    private val preferredCreationUserId: StateFlow<UserId?> =
        combine(matrixClients, accountViewModel.activeAccount) { clients, activeAccount ->
            activeAccount?.takeIf { clients.containsKey(it) } ?: clients.keys.firstOrNull()
        }.stateInScope(null)

    override val canUseSelectedGuildAccount: StateFlow<Boolean> =
        selectedGuildUserId.map { it != null }.stateInScope(false)

    override val canUsePreferredCreationAccount: StateFlow<Boolean> =
        preferredCreationUserId.map { it != null }.stateInScope(false)

    private fun guildClient(guild: GuildEntry): MatrixClient? =
        matrixClients.value[guild.userId]?.takeIf { guild.userId in selectedMatrixClientUserIds.value }

    private fun creationClient(): MatrixClient? = preferredCreationUserId.value?.let { matrixClients.value[it] }

    // ---- guild and category order: persisted in the messenger settings, overridden at once on reorder ----

    private val runtimeGuildOrder = MutableStateFlow<List<String>?>(null)
    private val guildOrder: Flow<List<String>> =
        combine(settings.map(::readTacitGuildOrder), runtimeGuildOrder) { persisted, runtime -> runtime ?: persisted }

    private val runtimeCategoryOrderByGuild = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val selectedGuildCategoryOrder: Flow<List<String>> =
        combine(selectedGuild, settings.map(::readTacitCategoryOrderByGuild), runtimeCategoryOrderByGuild) { guild, persisted, runtime ->
            guild?.key()?.let { (persisted + runtime)[it] }.orEmpty()
        }

    // ---- guilds: every space of the selected accounts that is not a category ----

    private val spaceGraphs: StateFlow<Map<UserId, TacitSpaceGraph>> = selectedMatrixClients
        .flatMapLatest { clients -> spaceHierarchy.spaceGraphs(clients) }
        .stateInScope(emptyMap())

    override val guilds: StateFlow<List<GuildEntry>> =
        combine(spaceGraphs, guildOrder) { graphs, order ->
            val discovered = graphs.flatMap { (userId, graph) -> graph.topLevelGuilds(userId) }
            orderGuilds(discovered, order)
        }.stateInScope(emptyList())

    override val guildAvatars: StateFlow<Map<String, ByteArray?>> =
        combine(guilds, selectedMatrixClients) { allGuilds, clients ->
            if (clients.isEmpty()) emptyList()
            else allGuilds.map { guild -> GuildAvatarInput(guild.key(), clients.find { it.userId == guild.userId }, guild.avatarUri) }
        }
            .distinctUntilChanged()
            .mapLatest { inputs ->
                inputs.mapConcurrently { input -> input.key to input.client?.loadAvatarThumbnail(input.avatarUri, 46L) }.toMap()
            }.stateInScope(emptyMap())

    /** Every room each guild places at any depth, by guild key. */
    private val roomIdsByGuild: StateFlow<Map<String, Set<RoomId>>> =
        combine(guilds, spaceGraphs) { guilds, graphs ->
            guilds.associate { guild -> guild.key() to (graphs[guild.userId] ?: TacitSpaceGraph.EMPTY).descendantsOf(guild.roomId) }
        }.stateInScope(emptyMap())

    /** Rooms placed in any guild; the DM home shows the complement, so no joined room is unreachable. */
    private val placedRoomIds: StateFlow<Set<RoomId>> =
        roomIdsByGuild.map { it.values.flatten().toSet() }.stateInScope(emptySet())

    // ---- the selected guild's categories ----

    /** The selected guild with the account that shows it; changes only when either does. */
    private val selectedGuildSource: Flow<Pair<RoomId, MatrixClient>?> =
        combine(selectedGuild, selectedGuildClient) { guild, client ->
            if (guild == null || client == null) null else guild.roomId to client
        }.distinctUntilChanged()

    private val selectedGuildLayout: StateFlow<TacitGuildLayout> = selectedGuildSource
        .flatMapLatest { source ->
            source?.let { (guildRoomId, client) -> spaceHierarchy.guildLayout(client, guildRoomId) } ?: flowOf(TacitGuildLayout.EMPTY)
        }.stateInScope(TacitGuildLayout.EMPTY)

    private val selectedGuildHierarchyNames: StateFlow<Map<RoomId, String>> = selectedGuildSource
        .mapLatest { source -> source?.let { (guildRoomId, client) -> spaceHierarchy.hierarchyNames(client, guildRoomId) }.orEmpty() }
        .stateInScope(emptyMap())

    // ---- rooms ----

    override val typedElements: StateFlow<List<TacitRoomListElementViewModel>> = delegate.elements
        .map { elements ->
            elements.map { element ->
                element as? TacitRoomListElementViewModel
                    ?: error("Expected TacitRoomListElementViewModel for room ${element.roomId.full}")
            }
        }
        .stateInScope(emptyList())

    private val roomDerived: StateFlow<List<TacitRoomDerived<TacitRoomListElementViewModel>>> = typedElements
        .flatMapLatest { rooms -> if (rooms.isEmpty()) flowOf(emptyList()) else combine(rooms.map { it.derived() }) { it.toList() } }
        .stateInScope(emptyList())

    private val selectedGuildRooms: Flow<List<TacitRoomDerived<TacitRoomListElementViewModel>>> =
        combine(roomDerived, selectedGuild, roomIdsByGuild) { rooms, guild, roomIdsByGuild ->
            if (guild == null) emptyList() else guildRooms(rooms, roomIdsByGuild[guild.key()].orEmpty())
        }

    override val visibleRooms: StateFlow<List<TacitRoomListElementViewModel>> =
        combine(roomDerived, selectedGuild, selectedGuildRooms, placedRoomIds) { rooms, guild, guildRooms, placed ->
            (if (guild == null) directMessageRooms(rooms, placed) else guildRooms).map { it.room }
        }.stateInScope(emptyList())

    override val guildChannelGroups: StateFlow<List<TacitGuildChannelGroup>> =
        combine(
            roomDerived,
            selectedGuildRooms,
            selectedGuildLayout,
            selectedGuildHierarchyNames,
            selectedGuildCategoryOrder,
            ::guildChannelGroups,
        ).stateInScope(emptyList())

    override val inviteRooms: StateFlow<List<TacitRoomListElementViewModel>> =
        combine(roomDerived, selectedGuild, selectedGuildLayout, placedRoomIds) { rooms, guild, layout, placed ->
            (if (guild == null) directMessageInvites(rooms, placed) else guildInvites(rooms, guild.roomId, layout)).map { it.room }
        }.stateInScope(emptyList())

    override val dmUnreadCount: StateFlow<Int> =
        combine(roomDerived, placedRoomIds, ::directMessagesUnreadCount).stateInScope(0)

    override val guildUnreadCounts: StateFlow<Map<String, Int>> =
        combine(roomDerived, guilds, roomIdsByGuild, ::guildUnreadCounts).stateInScope(emptyMap())

    // ---- dialogs and actions ----

    override val createGuildViewModel =
        TacitCreateGuildViewModel(childContext("tacitCreateGuild"), ::creationClient, onGuildCreated = ::selectGuild)
    override val createChannelViewModel =
        TacitCreateChannelViewModel(childContext("tacitCreateChannel"), ::guildClient, onRoomCreated = delegate::selectRoom)
    override val createCategoryViewModel =
        TacitCreateCategoryViewModel(childContext("tacitCreateCategory"), ::guildClient)
    override val createGroupChannelViewModel =
        TacitCreateGroupChannelViewModel(childContext("tacitCreateGroupChannel"), ::creationClient, onRoomCreated = delegate::selectRoom)
    override val createDirectMessageViewModel =
        TacitCreateDirectMessageViewModel(childContext("tacitCreateDirectMessage"), ::creationClient, onRoomCreated = delegate::selectRoom)
    override val inviteToGuildViewModel =
        TacitInviteToGuildViewModel(childContext("tacitInviteToGuild"), ::guildClient)
    override val browseChannelsViewModel = TacitBrowseChannelsViewModel(
        viewModelContext = childContext("tacitBrowseChannels"),
        rooms = roomDerived,
        guildLayout = selectedGuildLayout,
        hierarchyNames = selectedGuildHierarchyNames,
        selectedGuildClient = selectedGuildClient,
        guildClient = ::guildClient,
        onRoomJoined = delegate::selectRoom,
    )

    private val actions = TacitRoomListActions(
        scope = coroutineScope,
        i18n = tacitI18n,
        onGuildSelected = ::selectGuild,
        onRoomSelected = delegate::selectRoom,
        guildClient = ::guildClient,
    )

    override val reorderCategoriesInProgress: StateFlow<Boolean> = actions.moveRoomInProgress
    override val guildInviteActionInProgress: StateFlow<Boolean> = actions.guildInviteActionInProgress

    override fun acceptGuildInvite(guild: GuildEntry) = actions.acceptGuildInvite(guild)

    override fun declineGuildInvite(guild: GuildEntry) = actions.declineGuildInvite(guild)

    override fun moveRoomToCategory(guild: GuildEntry, roomId: RoomId, targetCategoryRoomId: RoomId) {
        // Drag-and-drop only targets categories of the selected guild, whose layout is tracked here.
        if (guild.roomId != selectedGuild.value?.roomId) return
        val currentCategoryRoomIds = selectedGuildLayout.value.categories.filterValues { roomId in it }.keys
        if (currentCategoryRoomIds == setOf(targetCategoryRoomId)) return
        actions.moveRoomToCategory(guild, roomId, targetCategoryRoomId, currentCategoryRoomIds)
    }

    override fun reorderCategories(guild: GuildEntry, orderedCategoryRoomIds: List<RoomId>) {
        val guildKey = guild.key()
        val orderedCategoryKeys = orderedCategoryRoomIds.map { it.full }
        runtimeCategoryOrderByGuild.update { it + (guildKey to orderedCategoryKeys) }
        coroutineScope.launch { settings.update { writeTacitCategoryOrderForGuild(guildKey, orderedCategoryKeys) } }
    }

    override fun reorderGuild(fromIndex: Int, toIndex: Int) {
        val current = guilds.value
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val reorderedKeys = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }.map { it.key() }
        runtimeGuildOrder.value = reorderedKeys
        coroutineScope.launch { settings.update { writeTacitGuildOrder(reorderedKeys) } }
    }

    override fun openSpaceSettings(spaceRoomId: RoomId) {
        spaceSettingsRequests.request(spaceRoomId)
        delegate.selectRoom(spaceRoomId)
    }

    // ---- errors: the latest of the dialogs' and actions' errors, else upstream's, shown in the room list ----

    private val errorSources = listOf(
        createGuildViewModel, createChannelViewModel, createCategoryViewModel, createGroupChannelViewModel,
        createDirectMessageViewModel, inviteToGuildViewModel, browseChannelsViewModel,
    ) + actions.errorSources

    private val tacitError = MutableStateFlow<String?>(null)
    private val tacitErrorType = MutableStateFlow(ErrorType.JUST_DISMISS)

    init {
        coroutineScope.launch { errorSources.map { it.error }.merge().collect { message -> reportError(message) } }
    }

    override val error: StateFlow<String?> =
        combine(delegate.error, tacitError) { delegateError, localError -> localError ?: delegateError }.stateInScope(null)

    override val errorType: StateFlow<ErrorType> =
        combine(delegate.error, delegate.errorType, tacitError, tacitErrorType) { delegateError, delegateType, localError, localType ->
            when {
                localError != null -> localType
                delegateError != null -> delegateType
                else -> ErrorType.JUST_DISMISS
            }
        }.stateInScope(ErrorType.JUST_DISMISS)

    override fun reportError(message: String?, errorType: ErrorType) {
        tacitErrorType.value = errorType
        tacitError.value = message
    }

    override fun errorDismiss() {
        tacitError.value = null
        errorSources.forEach { it.errorDismiss() }
        delegate.errorDismiss()
    }
}

/** The element's flows flattened into one [TacitRoomDerived]. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TacitRoomListElementViewModel.derived(): Flow<TacitRoomDerived<TacitRoomListElementViewModel>> =
    combine(isLoaded, roomName, isDirectRoom, isSpaceRoom, ::RoomIdentity)
        .flatMapLatest { identity ->
            combine(isInvite, isKnock, isLeave, isUnread) { isInvite, isKnock, isLeave, isUnread ->
                val isMembershipKnown = isInvite != null && isKnock != null && isLeave != null
                TacitRoomDerived(
                    room = this,
                    roomId = roomId,
                    roomName = identity.roomName,
                    isDirect = identity.isDirect,
                    isSpace = identity.isSpace,
                    isInvite = isInvite == true,
                    isKnock = isKnock == true,
                    isLeave = isLeave == true,
                    isJoined = identity.isLoaded && isMembershipKnown && isInvite != true && isKnock != true && isLeave != true,
                    isUnread = isUnread == true,
                )
            }
        }

private data class RoomIdentity(val isLoaded: Boolean, val roomName: String?, val isDirect: Boolean, val isSpace: Boolean)
