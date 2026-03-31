package org.fuchss.matrix.tacit.viewmodel.room.settings

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModel
import kotlinx.coroutines.flow.StateFlow

internal interface TacitRoomSettingsViewModel : RoomSettingsViewModel {
    val dmVerificationEntries: StateFlow<List<TacitDmVerificationEntry>>
    val isSpaceRoom: StateFlow<Boolean>
    val isGuildSpace: StateFlow<Boolean>
    val categoryTargets: StateFlow<List<TacitRoomCategoryTarget>>
    val currentCategoryRoomId: StateFlow<RoomId?>
    val moveToCategoryInProgress: StateFlow<Boolean>
    fun openDmVerification(userId: UserId)
    fun moveRoomToCategory(targetCategoryRoomId: RoomId?)
}

internal data class TacitRoomCategoryTarget(
    val roomId: RoomId,
    val displayName: String,
)

internal data class TacitDmVerificationEntry(
    val userId: UserId,
    val displayName: String,
    val initials: String,
    val image: ByteArray?,
    val status: TacitDmVerificationStatus,
)

internal enum class TacitDmVerificationStatus {
    VERIFIED,
    NEEDS_VERIFICATION,
    DEVICES_UNVERIFIED,
    INVALID,
    BLOCKED,
}
