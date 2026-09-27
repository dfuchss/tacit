package org.fuchss.matrix.tacit.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.util.EventReactions

/**
 * Marked [Immutable] on purpose: a fresh instance is constructed on every composition of
 * [rememberTacitFlatMessageUiState] and no field is ever mutated in place -- including
 * [senderAvatar], whose bytes are only ever read. Without the annotation Compose infers the whole
 * class as unstable (an array-typed field is unconditionally unstable, and `List` is unstable by
 * default), which makes [TacitFlatMessageContainer] -- the most frequently recomposed composable in
 * the app -- non-skippable.
 */
@Immutable
internal data class TacitFlatMessageUiState(
    val timelineElementHolder: TimelineElementHolderViewModel?,
    val redactionInProgress: Boolean,
    val showBigGap: Boolean,
    /**
     * `true` when this message directly follows another message of the same sender (and no big gap
     * separates them). Such rows are rendered in a compact, avatar-less form.
     */
    val isContinuation: Boolean,
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
    // `isFirstInUserSequence` is `false` only for a run of messages by the same sender. It starts
    // out as `null`, so we deliberately compare against `false` and fall back to the full (first in
    // sequence) rendering while it is unknown. A big gap -- a sender change or a long pause --
    // always breaks the group again.
    val isFirstInUserSequence = holder.isFirstInUserSequence.collectAsState().value
    val isContinuation = isFirstInUserSequence == false && !showBigGap
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
        isContinuation = isContinuation,
        showRedactionWarning = showRedactionWarning,
        reactionEntries = reactionEntries,
        senderInitials = senderInitials,
        senderAvatar = senderAvatar,
    )
}
