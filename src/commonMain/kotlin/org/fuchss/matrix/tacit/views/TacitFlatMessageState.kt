package org.fuchss.matrix.tacit.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.util.EventReactions

internal data class TacitFlatMessageUiState(
    val timelineElementHolder: TimelineElementHolderViewModel?,
    val redactionInProgress: Boolean,
    val showBigGap: Boolean,
    val showRedactionWarning: Boolean,
    val reactionEntries: List<Map.Entry<String, Set<EventReactions.ByReactionInfo>>>,
    val senderInitials: String?,
    val senderAvatar: ByteArray?,
) {
    val hasReactions: Boolean
        get() = reactionEntries.isNotEmpty()
}

@Composable
internal fun rememberTacitFlatMessageUiState(
    holder: BaseTimelineElementHolderViewModel,
): TacitFlatMessageUiState {
    val timelineElementHolder = remember(holder) { holder as? TimelineElementHolderViewModel }

    val redactionInProgress = timelineElementHolder?.redactionInProgress?.collectAsState()?.value == true
    val showBigGap = holder.showBigGapBefore.collectAsState().value == true
    val showRedactionWarning = timelineElementHolder?.showRedactionWarning?.collectAsState()?.value == true
    val reactionEntries = timelineElementHolder
        ?.reactions
        ?.collectAsState()
        ?.value
        ?.byReaction
        ?.entries
        ?.sortedByDescending { it.value.size }
        .orEmpty()

    val sender = holder.sender.collectAsState().value
    val senderInitials = sender?.initials
    val senderAvatar = sender?.image?.collectAsState(null)?.value

    return TacitFlatMessageUiState(
        timelineElementHolder = timelineElementHolder,
        redactionInProgress = redactionInProgress,
        showBigGap = showBigGap,
        showRedactionWarning = showRedactionWarning,
        reactionEntries = reactionEntries,
        senderInitials = senderInitials,
        senderAvatar = senderAvatar,
    )
}
