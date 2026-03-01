package org.fuchss.matrix.tacit.viewmodel.room.settings

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
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed

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
        return TacitRoomSettingsViewModelImpl(delegate, viewModelContext)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TacitRoomSettingsViewModelImpl(
    private val delegate: RoomSettingsViewModel,
    viewModelContext: MatrixClientViewModelContext,
) : TacitRoomSettingsViewModel,
    RoomSettingsViewModel by delegate,
    MatrixClientViewModelContext by viewModelContext {

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
