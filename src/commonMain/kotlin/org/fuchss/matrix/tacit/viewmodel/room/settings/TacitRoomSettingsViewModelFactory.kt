package org.fuchss.matrix.tacit.viewmodel.room.settings

import org.fuchss.matrix.tacit.viewmodel.util.initialsOf
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.crypto.key.UserTrustLevel
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.room.settings.OpenAvatarCutterCallback
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OpenMentionCallback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.fuchss.matrix.tacit.viewmodel.util.tacitWhileSubscribed
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.space.TacitSpaceHierarchy
import org.fuchss.matrix.tacit.viewmodel.space.isSpace
import org.fuchss.matrix.tacit.viewmodel.util.launchExclusive
import org.fuchss.matrix.tacit.viewmodel.util.moveRoomToCategory
import org.koin.core.component.get

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
        return TacitRoomSettingsViewModelImpl(delegate, viewModelContext, selectedRoomId, onCloseRoom)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TacitRoomSettingsViewModelImpl(
    private val delegate: RoomSettingsViewModel,
    viewModelContext: MatrixClientViewModelContext,
    private val selectedRoomId: RoomId,
    private val onCloseRoom: () -> Unit,
) : TacitRoomSettingsViewModel,
    RoomSettingsViewModel by delegate,
    MatrixClientViewModelContext by viewModelContext {

    private val tacitI18n = get<TacitI18n>()

    override fun closeSettingsAndRoom() {
        delegate.close()
        onCloseRoom()
    }

    private val spaceHierarchy = get<TacitSpaceHierarchy>()

    override val isSpaceRoom: StateFlow<Boolean> = matrixClient.room.getById(selectedRoomId)
        .map { room -> room?.isSpace == true }
        .stateIn(coroutineScope, tacitWhileSubscribed, false)

    private val guildRoomId: StateFlow<RoomId?> = spaceHierarchy.guildOf(matrixClient, selectedRoomId)
        .stateIn(coroutineScope, tacitWhileSubscribed, null)

    override val isGuildSpace: StateFlow<Boolean> = combine(isSpaceRoom, guildRoomId) { isSpace, guildId ->
        isSpace && (guildId == selectedRoomId || guildId == null)
    }
        .stateIn(coroutineScope, tacitWhileSubscribed, false)

    /** The guild's categories (except this room itself) with their children. */
    private val categoryChildrenByRoomId: StateFlow<Map<RoomId, Set<RoomId>>> = guildRoomId
        .flatMapLatest { guildId ->
            if (guildId == null) flowOf(emptyMap())
            else spaceHierarchy.guildLayout(matrixClient, guildId).map { layout -> layout.categories - selectedRoomId }
        }
        .stateIn(coroutineScope, tacitWhileSubscribed, emptyMap())

    override val currentCategoryRoomId: StateFlow<RoomId?> = categoryChildrenByRoomId
        .map { categoryChildren ->
            categoryChildren.entries.firstOrNull { (_, childRoomIds) -> childRoomIds.contains(selectedRoomId) }?.key
        }
        .stateIn(coroutineScope, tacitWhileSubscribed, null)

    override val categoryTargets: StateFlow<List<TacitRoomCategoryTarget>> =
        combine(guildRoomId, categoryChildrenByRoomId.map { it.keys }.distinctUntilChanged()) { guildId, categories ->
            guildId to categories
        }
            .mapLatest { (guildId, categoryRoomIds) ->
                if (guildId == null || categoryRoomIds.isEmpty()) return@mapLatest emptyList()
                val namesByRoomId = spaceHierarchy.hierarchyNames(matrixClient, guildId)
                categoryRoomIds
                    .map { categoryRoomId ->
                        TacitRoomCategoryTarget(
                            roomId = categoryRoomId,
                            displayName = namesByRoomId[categoryRoomId] ?: categoryRoomId.full,
                        )
                    }
                    .sortedBy { it.displayName.lowercase() }
            }
            .stateIn(coroutineScope, tacitWhileSubscribed, emptyList())

    private val moveToCategoryError = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = combine(delegate.error, moveToCategoryError) { delegateError, localError ->
        localError ?: delegateError
    }.stateIn(coroutineScope, tacitWhileSubscribed, delegate.error.value)

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
                                initials = member?.initials ?: initialsOf(displayName),
                                image = member?.image,
                                status = trustLevel.toDmVerificationStatus(),
                            )
                        }
                    }
                ) { entries ->
                    entries.toList().sortedBy { it.displayName.lowercase() }
                }
            }
            .stateIn(coroutineScope, tacitWhileSubscribed, emptyList())

    override fun openDmVerification(userId: UserId) {
        delegate.openUserProfile(userId)
    }

    override fun moveRoomToCategory(targetCategoryRoomId: RoomId?) {
        if (currentCategoryRoomId.value == targetCategoryRoomId) return
        if (guildRoomId.value == null) return
        val currentCategoryRoomIds = categoryChildrenByRoomId.value
            .filterValues { childRoomIds -> selectedRoomId in childRoomIds }
            .keys

        coroutineScope.launchExclusive(_moveToCategoryInProgress) {
            moveToCategoryError.value = null
            moveRoomToCategory(
                matrixClient = matrixClient,
                roomId = selectedRoomId,
                targetCategoryRoomId = targetCategoryRoomId,
                currentCategoryRoomIds = currentCategoryRoomIds,
            ).onFailure { throwable ->
                moveToCategoryError.value = tacitI18n.tacitMoveRoomToCategoryFailed(tacitI18n.tacitErrorMessage(throwable))
            }
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
