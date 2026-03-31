package org.fuchss.matrix.tacit.viewmodel.room.settings

import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent.RoomType
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.space.ParentEventContent
import de.connect2x.trixnity.crypto.key.UserTrustLevel
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.room.settings.OpenAvatarCutterCallback
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OpenMentionCallback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.moveRoomToCategory

internal object TacitRoomSettingsViewModelFactory : RoomSettingsViewModelFactory {
    override fun create(
        viewModelContext: MatrixClientViewModelContext,
        selectedRoomId: RoomId,
        onCloseRoom: () -> Unit,
        onOpenAddMembers: () -> Unit,
        onOpenDevInfo: () -> Unit,
        onOpenExportRoom: () -> Unit,
        onCloseRoomSettings: () -> Unit,
        onOpenUserProfile: (UserId) -> Unit,
        onOpenAvatarCutter: OpenAvatarCutterCallback,
        onOpenPowerLevel: () -> Unit,
        onOpenMention: OpenMentionCallback,
    ): RoomSettingsViewModel {
        val delegate = RoomSettingsViewModelFactory.create(
            viewModelContext = viewModelContext,
            selectedRoomId = selectedRoomId,
            onCloseRoom = onCloseRoom,
            onOpenAddMembers = onOpenAddMembers,
            onOpenDevInfo = onOpenDevInfo,
            onOpenExportRoom = onOpenExportRoom,
            onCloseRoomSettings = onCloseRoomSettings,
            onOpenUserProfile = onOpenUserProfile,
            onOpenAvatarCutter = onOpenAvatarCutter,
            onOpenPowerLevel = onOpenPowerLevel,
            onOpenMention = onOpenMention,
        )
        return TacitRoomSettingsViewModelImpl(delegate, viewModelContext, selectedRoomId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TacitRoomSettingsViewModelImpl(
    private val delegate: RoomSettingsViewModel,
    viewModelContext: MatrixClientViewModelContext,
    private val selectedRoomId: RoomId,
) : TacitRoomSettingsViewModel,
    RoomSettingsViewModel by delegate,
    MatrixClientViewModelContext by viewModelContext {

    private val parentRoomIds: StateFlow<List<Pair<RoomId, Boolean>>> = matrixClient.room
        .getAllState(selectedRoomId, ParentEventContent::class)
        .flattenValues()
        .map { parentEvents ->
            parentEvents.mapNotNull { event ->
                val parentRoomId = runCatching { RoomId(event.stateKey) }.getOrNull() ?: return@mapNotNull null
                parentRoomId to event.content.canonical
            }
        }
        .stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override val isSpaceRoom: StateFlow<Boolean> = matrixClient.room.getById(selectedRoomId)
        .map { room -> room?.createEventContent?.type == RoomType.Space }
        .stateIn(coroutineScope, WhileSubscribed(), false)

    private val guildRoomId: StateFlow<RoomId?> = parentRoomIds
        .mapLatest { parents ->
            var current = parents.firstOrNull { it.second }?.first ?: parents.firstOrNull()?.first
            val visited = mutableSetOf<RoomId>()

            while (current != null && visited.add(current)) {
                val parentEvents = matrixClient.room
                    .getAllState(current, ParentEventContent::class)
                    .flattenValues()
                    .first()

                val nextParents = parentEvents.mapNotNull { event ->
                    val parentRoomId = runCatching { RoomId(event.stateKey) }.getOrNull() ?: return@mapNotNull null
                    parentRoomId to event.content.canonical
                }

                val next = nextParents.firstOrNull { it.second }?.first ?: nextParents.firstOrNull()?.first
                if (next == null) return@mapLatest current
                current = next
            }

            current
        }
        .stateIn(coroutineScope, WhileSubscribed(), null)

    override val isGuildSpace: StateFlow<Boolean> = combine(isSpaceRoom, guildRoomId) { isSpace, guildId ->
        isSpace && (guildId == selectedRoomId || guildId == null)
    }
        .stateIn(coroutineScope, WhileSubscribed(), false)

    private val categoryChildrenByRoomId: StateFlow<Map<RoomId, Set<RoomId>>> = guildRoomId
        .flatMapLatest { guildId ->
            if (guildId == null) {
                flowOf(emptyMap())
            } else {
                matrixClient.room.getAllState(guildId, ChildEventContent::class)
                    .flattenValues()
                    .flatMapLatest { childEvents ->
                        val childIds = childEvents
                            .mapNotNull { runCatching { RoomId(it.stateKey) }.getOrNull() }
                            .filter { it != selectedRoomId }
                            .distinct()

                        if (childIds.isEmpty()) {
                            flowOf(emptyMap())
                        } else {
                            combine(childIds.map { childId ->
                                combine(
                                    matrixClient.room.getById(childId),
                                    matrixClient.room.getAllState(childId, ChildEventContent::class).flattenValues(),
                                ) { room, categoryChildEvents ->
                                    val isSpace = room?.createEventContent?.type == RoomType.Space
                                    val children = categoryChildEvents
                                        .filter { it.content.via.isNotEmpty() }
                                        .mapNotNull { runCatching { RoomId(it.stateKey) }.getOrNull() }
                                        .toSet()
                                    childId to if (isSpace) children else null
                                }
                            }) { categoryStates ->
                                categoryStates
                                    .mapNotNull { (categoryRoomId, children) -> children?.let { categoryRoomId to it } }
                                    .toMap()
                            }
                        }
                    }
            }
        }
        .stateIn(coroutineScope, WhileSubscribed(), emptyMap())

    override val currentCategoryRoomId: StateFlow<RoomId?> = categoryChildrenByRoomId
        .map { categoryChildren ->
            categoryChildren.entries.firstOrNull { (_, childRoomIds) -> childRoomIds.contains(selectedRoomId) }?.key
        }
        .stateIn(coroutineScope, WhileSubscribed(), null)

    override val categoryTargets: StateFlow<List<TacitRoomCategoryTarget>> = guildRoomId
        .flatMapLatest { guildId ->
            if (guildId == null) {
                flowOf(emptyList())
            } else {
                categoryChildrenByRoomId.mapLatest { categoryChildren ->
                    val categoryRoomIds = categoryChildren.keys.toList()
                    if (categoryRoomIds.isEmpty()) {
                        emptyList()
                    } else {
                        val namesByRoomId = matrixClient.api.room
                            .getHierarchy(roomId = guildId, limit = 200)
                            .getOrNull()
                            ?.rooms
                            ?.associate { info ->
                                val resolvedName = info.name?.takeIf { it.isNotBlank() } ?: info.roomId.full
                                info.roomId to resolvedName
                            }
                            .orEmpty()

                        categoryRoomIds
                            .map { categoryRoomId ->
                                TacitRoomCategoryTarget(
                                    roomId = categoryRoomId,
                                    displayName = namesByRoomId[categoryRoomId] ?: categoryRoomId.full,
                                )
                            }
                            .sortedBy { it.displayName.lowercase() }
                    }
                }
            }
        }
        .stateIn(coroutineScope, WhileSubscribed(), emptyList())

    private val _moveToCategoryInProgress = MutableStateFlow(false)
    override val moveToCategoryInProgress: StateFlow<Boolean> = _moveToCategoryInProgress

    override val dmVerificationEntries: StateFlow<List<TacitDmVerificationEntry>> =
        delegate.memberListViewModel.elements
            .flatMapLatest { memberElements ->
                val dmPeers = memberElements.filter { it.iHavePowerToBlockUser }
                if (dmPeers.isEmpty()) flowOf(emptyList())
                else combine(
                    dmPeers.map { memberViewModel ->
                        combine(memberViewModel.member, memberViewModel.userTrustLevel) { member, trustLevel ->
                            val displayName = member?.displayName?.ifBlank { null } ?: memberViewModel.memberUserId.full
                            TacitDmVerificationEntry(
                                userId = memberViewModel.memberUserId,
                                displayName = displayName,
                                initials = member?.initials ?: displayName.initialsFromName(),
                                image = member?.image,
                                status = trustLevel.toDmVerificationStatus(),
                            )
                        }
                    }
                ) { entries ->
                    entries.toList().sortedBy { it.displayName.lowercase() }
                }
            }
            .stateIn(coroutineScope, WhileSubscribed(), emptyList())

    override fun openDmVerification(userId: UserId) {
        delegate.openUserProfile(userId)
    }

    override fun moveRoomToCategory(targetCategoryRoomId: RoomId?) {
        if (_moveToCategoryInProgress.value) return
        if (currentCategoryRoomId.value == targetCategoryRoomId) return

        val guildId = guildRoomId.value ?: return
        GuildEntry(
            roomId = guildId,
            userId = matrixClient.userId,
            displayName = null,
            avatarUri = null,
            membership = Membership.JOIN,
        )

        coroutineScope.launch {
            _moveToCategoryInProgress.value = true
            moveRoomToCategory(
                matrixClient = matrixClient,
                roomId = selectedRoomId,
                targetCategoryRoomId = targetCategoryRoomId,
                currentCategoryRoomId = currentCategoryRoomId.value,
            )
            _moveToCategoryInProgress.value = false
        }
    }
}

private fun UserTrustLevel?.toDmVerificationStatus(): TacitDmVerificationStatus = when (this) {
    is UserTrustLevel.CrossSigned -> if (verified) {
        TacitDmVerificationStatus.VERIFIED
    } else {
        TacitDmVerificationStatus.NEEDS_VERIFICATION
    }

    is UserTrustLevel.NotAllDevicesCrossSigned -> TacitDmVerificationStatus.DEVICES_UNVERIFIED
    is UserTrustLevel.Invalid -> TacitDmVerificationStatus.INVALID
    UserTrustLevel.Blocked -> TacitDmVerificationStatus.BLOCKED
    UserTrustLevel.Unknown, null -> TacitDmVerificationStatus.NEEDS_VERIFICATION
}

private fun String.initialsFromName(): String {
    val parts = split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
