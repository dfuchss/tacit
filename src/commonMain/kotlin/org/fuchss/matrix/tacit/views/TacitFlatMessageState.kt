package org.fuchss.matrix.tacit.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OutboxElementHolderViewModel
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
    /**
     * Non-null for an event that is already part of the timeline. `null` for an *outbox* element --
     * a message that is still being sent, is uploading, or failed to send. Those two cases are
     * disjoint branches of the same `sealed interface BaseTimelineElementHolderViewModel`, which is
     * why [outboxElementHolder] exists next to this instead of a single flag: an outbox element has
     * no event id yet, so none of the reply/edit/redact/report capabilities apply to it, while
     * retry/abort apply only to it.
     */
    val timelineElementHolder: TimelineElementHolderViewModel?,
    /** Non-null for a pending, uploading or failed message. See [timelineElementHolder]. */
    val outboxElementHolder: OutboxElementHolderViewModel?,
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
    /**
     * Localized error of the last send attempt, or `null` when nothing went wrong. Read from
     * [BaseTimelineElementHolderViewModel] rather than from the outbox holder so that a failed
     * *edit* of an already sent event is covered too.
     */
    val sendError: String?,
    /** `false` while an own message has not reached the server yet. Always `true` for foreign ones. */
    val isSent: Boolean,
    val canRetrySend: Boolean,
    val canAbortSend: Boolean,
    /**
     * Upload progress of a media send, split into primitives on purpose: the upstream
     * `FileTransferProgressElement` is an ordinary data class of another module and therefore
     * inferred unstable, which would defeat the [Immutable] contract of this class.
     * `null` when nothing is uploading; [uploadPercent] alone is `null` for an indeterminate upload.
     */
    val uploadPercent: Float?,
    val uploadProgressLabel: String?,
) {
    val hasReactions: Boolean
        get() = reactionEntries.isNotEmpty()

    /** The send failed and the message only exists locally -- it is recoverable via retry/abort. */
    val hasSendError: Boolean
        get() = sendError != null

    /** The message is on its way out but has not been acknowledged by the server yet. */
    val isSending: Boolean
        get() = !isSent && sendError == null

    val isUploading: Boolean
        get() = uploadProgressLabel != null || uploadPercent != null
}

@Composable
internal fun rememberTacitFlatMessageUiState(
    holder: BaseTimelineElementHolderViewModel,
): TacitFlatMessageUiState {
    val timelineElementHolder = remember(holder) { holder as? TimelineElementHolderViewModel }
    val outboxElementHolder = remember(holder) { holder as? OutboxElementHolderViewModel }

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

    val sendError = holder.sendError.collectAsState().value
    val isSent = holder.isSent.collectAsState().value
    val canRetrySend = outboxElementHolder?.canRetrySend?.collectAsState()?.value == true
    val canAbortSend = outboxElementHolder?.canAbortSend?.collectAsState()?.value == true
    val uploadProgress = outboxElementHolder?.uploadProgress?.collectAsState()?.value

    return TacitFlatMessageUiState(
        timelineElementHolder = timelineElementHolder,
        outboxElementHolder = outboxElementHolder,
        redactionInProgress = redactionInProgress,
        showBigGap = showBigGap,
        isContinuation = isContinuation,
        showRedactionWarning = showRedactionWarning,
        reactionEntries = reactionEntries,
        senderInitials = senderInitials,
        senderAvatar = senderAvatar,
        sendError = sendError,
        isSent = isSent,
        canRetrySend = canRetrySend,
        canAbortSend = canAbortSend,
        uploadPercent = uploadProgress?.percent,
        uploadProgressLabel = uploadProgress?.formattedProgress,
    )
}
