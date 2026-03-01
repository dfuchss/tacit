package org.fuchss.matrix.tacit.viewmodel.room.settings

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModel
import kotlinx.coroutines.flow.StateFlow

internal interface TacitRoomSettingsViewModel : RoomSettingsViewModel {
    val dmVerificationEntries: StateFlow<List<TacitDmVerificationEntry>>
    fun openDmVerification(userId: UserId)
}

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
