package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.room.Membership
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

    /** Everything the room list view renders, combined from the flows above. */
    val uiState: StateFlow<TacitRoomListUiState>

    /** Scroll positions of the room list per account and guild; outlives the view's composition. */
    val scrollMemory: TacitScrollMemory

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
    /**
     * Moves the guild with [guildKey] to [toIndex]. The guild is resolved by its key against the current guild list,
     * so a list that changed during the drag cannot move the wrong guild.
     */
    fun reorderGuild(guildKey: String, toIndex: Int)
    fun reorderCategory(fromIndex: Int, toIndex: Int)

    /** Keeps the selected guild in sync with [guilds]: refreshes its entry, or deselects it once it is gone. */
    fun refreshSelectedGuild(guilds: List<GuildEntry>)

    /** Picks a DM filter; picking the active one again goes back to [DmFilter.ALL]. */
    fun selectDmFilter(requested: DmFilter)

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
        spaceSettingsRequests = spaceSettingsRequests,
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

    override fun reorderCategory(fromIndex: Int, toIndex: Int) {
        val guild = selectedGuild.value ?: return
        if (reorderCategoriesInProgress.value) return
        val ordered = categoryOrderAfterMove(guildChannelGroups.value, fromIndex, toIndex) ?: return
        reorderCategories(guild, ordered)
    }

    override fun reorderGuild(guildKey: String, toIndex: Int) {
        val reorderedKeys = guildOrderAfterMove(guilds.value, guildKey, toIndex) ?: return
        runtimeGuildOrder.value = reorderedKeys
        coroutineScope.launch { settings.update { writeTacitGuildOrder(reorderedKeys) } }
    }

    override fun refreshSelectedGuild(guilds: List<GuildEntry>) {
        val selected = selectedGuild.value ?: return
        val refreshed = reselectGuild(selected, guilds)
        if (refreshed != selected) selectGuild(refreshed)
    }

    override fun openSpaceSettings(spaceRoomId: RoomId) {
        if (canRequestSpaceSettings(selectedGuild.value, spaceRoomId)) spaceSettingsRequests.request(spaceRoomId)
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

    // ---- the view's state: collected once by the room list view ----

    private val dmFilter = MutableStateFlow(DmFilter.ALL)

    override fun selectDmFilter(requested: DmFilter) {
        dmFilter.update { it.toggled(requested) }
    }

    override val scrollMemory = TacitScrollMemory()

    /** The visible rooms with, in the DM home, each room's unread and online state for the DM filters. */
    private val visibleRoomsWithDirectMessages: Flow<Pair<List<TacitRoomListElementViewModel>, List<TacitDmRoom<TacitRoomListElementViewModel>>>> =
        combine(selectedGuild, visibleRooms, ::Pair).flatMapLatest { (guild, rooms) ->
            if (guild != null || rooms.isEmpty()) flowOf(rooms to emptyList())
            else combine(rooms.map { it.directMessage() }) { rooms to it.toList() }
        }

    private val railState: Flow<TacitGuildRailState> =
        combine(selectedGuild, guilds, guildAvatars, dmUnreadCount, guildUnreadCounts, ::TacitGuildRailState)

    private val roomsState: Flow<TacitRoomListRooms> =
        combine(typedElements, visibleRoomsWithDirectMessages, guildChannelGroups, inviteRooms) { all, (visible, directMessages), groups, invites ->
            TacitRoomListRooms(
                allRoomsEmpty = all.isEmpty(),
                visibleRooms = visible,
                guildChannelGroups = groups,
                inviteRooms = invites,
                directMessages = directMessages,
            )
        }

    private val statusState: Flow<TacitRoomListStatus> =
        combine(
            combine(selectedRoomId, canCreateNewRoomWithAccount, searchResultsEmpty, syncStates, ::Quadruple),
            combine(canUseSelectedGuildAccount, canUsePreferredCreationAccount, reorderCategoriesInProgress, guildInviteActionInProgress, ::Quadruple),
        ) { (selectedRoomId, canCreateRoom, searchResultsEmpty, syncStates), (selectedGuildAccount, creationAccount, reordering, answeringInvite) ->
            TacitRoomListStatus(
                selectedRoomId = selectedRoomId,
                canCreateNewRoomWithAccount = canCreateRoom,
                searchResultsEmpty = searchResultsEmpty,
                canUseSelectedGuildAccount = selectedGuildAccount,
                canUsePreferredCreationAccount = creationAccount,
                reorderCategoriesInProgress = reordering,
                guildInviteActionInProgress = answeringInvite,
                syncStates = syncStates,
            )
        }

    private val errorState: Flow<TacitRoomListError> = combine(error, errorType, ::TacitRoomListError)

    override val uiState: StateFlow<TacitRoomListUiState> =
        combine(
            combine(railState, roomsState, ::Pair),
            combine(statusState, errorState, ::Pair),
            dmFilter,
            accountViewModel.activeAccount,
            browseChannelsViewModel.channels,
        ) { (rail, rooms), (status, error), dmFilter, activeAccount, browseChannels ->
            val mode = rail.selectedGuild?.let { RoomListMode.GuildChannels(it) } ?: RoomListMode.DirectMessages
            TacitRoomListUiState(
                rail = rail,
                rooms = rooms,
                status = status,
                error = error,
                dmFilter = dmFilter,
                scrollKey = roomListScrollKey(mode, activeAccount),
                browseChannels = browseChannels,
            )
        }.stateInScope(TacitRoomListUiState())
}

/**
 * Whether opening [spaceRoomId] may file a settings request: not for the [selectedGuild] while it is not joined, since
 * upstream then shows its join prompt instead of a room view model that would consume the request.
 */
internal fun canRequestSpaceSettings(selectedGuild: GuildEntry?, spaceRoomId: RoomId): Boolean =
    selectedGuild?.roomId != spaceRoomId || selectedGuild.membership == Membership.JOIN

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** The element's unread and online state, for the DM filters. */
private fun TacitRoomListElementViewModel.directMessage(): Flow<TacitDmRoom<TacitRoomListElementViewModel>> =
    combine(isUnread, presence) { isUnread, presence ->
        TacitDmRoom(this, isUnread = isUnread == true, isOnline = presence == Presence.ONLINE)
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
