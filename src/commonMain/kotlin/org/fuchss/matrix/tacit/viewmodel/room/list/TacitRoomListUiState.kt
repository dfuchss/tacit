package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel.UserSyncStates
import de.connect2x.trixnity.messenger.viewmodel.util.ErrorType
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry

/** What the guild rail shows. */
internal data class TacitGuildRailState(
    val selectedGuild: GuildEntry? = null,
    val guilds: List<GuildEntry> = emptyList(),
    val guildAvatars: Map<String, ByteArray?> = emptyMap(),
    val dmUnreadCount: Int = 0,
    val guildUnreadCounts: Map<String, Int> = emptyMap(),
)

/** The rooms of the room list for the selected guild or the DM home. */
internal data class TacitRoomListRooms(
    val allRoomsEmpty: Boolean = true,
    val visibleRooms: List<TacitRoomListElementViewModel> = emptyList(),
    val guildChannelGroups: List<TacitGuildChannelGroup> = emptyList(),
    val inviteRooms: List<TacitRoomListElementViewModel> = emptyList(),
    /** The DM home's rooms with their unread and online state; empty for a guild. */
    val directMessages: List<TacitDmRoom<TacitRoomListElementViewModel>> = emptyList(),
)

/** Account, search and progress flags of the room list. */
internal data class TacitRoomListStatus(
    val selectedRoomId: RoomId? = null,
    val canCreateNewRoomWithAccount: Boolean = false,
    val searchResultsEmpty: Boolean = false,
    val canUseSelectedGuildAccount: Boolean = false,
    val canUsePreferredCreationAccount: Boolean = false,
    val guildInviteActionInProgress: Boolean = false,
    val syncStates: UserSyncStates = UserSyncStates(emptySet(), emptySet()),
)

/** The room list's error banner. */
internal data class TacitRoomListError(
    val message: String? = null,
    val type: ErrorType = ErrorType.JUST_DISMISS,
)

/** Everything the room list renders, collected once at its top. */
internal data class TacitRoomListUiState(
    val rail: TacitGuildRailState = TacitGuildRailState(),
    val rooms: TacitRoomListRooms = TacitRoomListRooms(),
    val status: TacitRoomListStatus = TacitRoomListStatus(),
    val error: TacitRoomListError = TacitRoomListError(),
    val dmFilter: DmFilter = DmFilter.ALL,
    /** Key under which the room list's scroll position is remembered, per account and guild. */
    val scrollKey: String = roomListScrollKey(RoomListMode.DirectMessages, activeAccount = null),
    val browseChannels: List<SpaceChannelEntry> = emptyList(),
) {
    val selectedGuild: GuildEntry? get() = rail.selectedGuild

    val mode: RoomListMode = rail.selectedGuild?.let { RoomListMode.GuildChannels(it) } ?: RoomListMode.DirectMessages

    /** The selected guild if it is an invite that the room list does not show as an invite row of its own. */
    val selectedGuildInviteFallback: GuildEntry? =
        rail.selectedGuild?.takeIf { guild -> guild.isInvite && rooms.inviteRooms.none { it.roomId == guild.roomId } }

    /** The visible rooms after the DM filter; a guild's rooms are not filtered. */
    val filteredRooms: List<TacitRoomListElementViewModel> =
        if (mode.isDirectMessages() && dmFilter != DmFilter.ALL) rooms.directMessages.filteredBy(dmFilter) else rooms.visibleRooms

    val dmOverview: TacitDmOverview = TacitDmOverview.of(rooms.visibleRooms.size, rooms.directMessages)
}

/** The quick filters of the DM home. */
internal enum class DmFilter {
    ALL,
    ONLINE,
    UNREAD,
    ;

    /** The filter after the user picked [requested]: picking the active filter again goes back to [ALL]. */
    fun toggled(requested: DmFilter): DmFilter = if (requested == this && requested != ALL) ALL else requested
}

/** A room of the DM home with the state its filters need. */
internal data class TacitDmRoom<out R>(val room: R, val isUnread: Boolean, val isOnline: Boolean)

internal fun <R> List<TacitDmRoom<R>>.filteredBy(filter: DmFilter): List<R> = filter { room ->
    when (filter) {
        DmFilter.ALL -> true
        DmFilter.UNREAD -> room.isUnread
        DmFilter.ONLINE -> room.isOnline
    }
}.map { it.room }

/** The counters of the DM home's stat tiles. */
internal data class TacitDmOverview(val chats: Int = 0, val online: Int = 0, val unread: Int = 0) {
    companion object {
        fun of(chats: Int, rooms: List<TacitDmRoom<*>>) =
            TacitDmOverview(chats = chats, online = rooms.count { it.isOnline }, unread = rooms.count { it.isUnread })
    }
}

/** The DM home is remembered per account ([activeAccount] null means all accounts); guild keys carry their account. */
internal fun roomListScrollKey(mode: RoomListMode, activeAccount: UserId?): String = when (mode) {
    is RoomListMode.DirectMessages -> "dm:${activeAccount?.full ?: "*"}"
    is RoomListMode.GuildChannels -> "guild:${mode.guild.key()}"
}

/** First visible item and its offset of a scrolled list. */
internal data class TacitScrollPosition(val index: Int, val offset: Int)

/**
 * Scroll positions of the room list that outlive its composition (it is composed from two call sites across the two
 * pane breakpoint). Keeps the [capacity] most recently saved keys, so switching guilds and accounts cannot grow it.
 */
internal class TacitScrollMemory(private val capacity: Int = DEFAULT_CAPACITY) {
    private val positions = LinkedHashMap<String, TacitScrollPosition>()

    operator fun get(key: String): TacitScrollPosition? = positions[key]

    operator fun set(key: String, position: TacitScrollPosition) {
        positions.remove(key)
        positions[key] = position
        while (positions.size > capacity) positions.remove(positions.keys.first())
    }

    val size: Int get() = positions.size

    companion object {
        const val DEFAULT_CAPACITY = 32
    }
}
