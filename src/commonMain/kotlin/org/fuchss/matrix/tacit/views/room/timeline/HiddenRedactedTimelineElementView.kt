package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.RedactedMessageTimelineElementView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.RedactedStateTimelineElementView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.ReferencedMessagePill
import de.connect2x.trixnity.messenger.i18n.I18n
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.RedactedMessageTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.RedactedStateTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.RedactedTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementViewModel
import kotlin.reflect.KClass
import org.fuchss.matrix.tacit.tacit

/**
 * Hides redacted messages and redacted state events in the timeline. Upstream splits the redacted view in a message and
 * a state variant; Tacit registers this view for both.
 */
internal sealed class TacitHiddenRedactedTimelineElementView<T> : TimelineElementView<T>
    where T : TimelineElementViewModel<*>, T : RedactedTimelineElementViewModel {

    override suspend fun waitFor(element: T) = Unit

    override fun isFocusable(): Boolean = false

    @Composable
    override fun createInTimeline(holder: BaseTimelineElementHolderViewModel, element: T, index: Int) = Unit

    @Composable
    override fun createAsPreview(holder: TimelineElementHolderViewModel, element: T, index: Int) = Unit

    @Composable
    override fun createReplyInTimeline(
        holder: TimelineElementHolderViewModel,
        element: T,
        modifier: Modifier,
        interactionSource: MutableInteractionSource,
    ) = DeletedReplyPreview(holder, element, modifier, interactionSource)

    @Composable
    override fun createReplyInSendMessage(
        holder: TimelineElementHolderViewModel,
        element: T,
        modifier: Modifier,
        interactionSource: MutableInteractionSource,
    ) = DeletedReplyPreview(holder, element, modifier, interactionSource)

    @Composable
    override fun getClipEntry(holder: BaseTimelineElementHolderViewModel, element: T): ClipEntry? = null

    object Message :
        TacitHiddenRedactedTimelineElementView<RedactedMessageTimelineElementViewModel>(),
        RedactedMessageTimelineElementView {
        override val supports: KClass<RedactedMessageTimelineElementViewModel> =
            RedactedMessageTimelineElementViewModel::class
    }

    object State :
        TacitHiddenRedactedTimelineElementView<RedactedStateTimelineElementViewModel>(),
        RedactedStateTimelineElementView {
        override val supports: KClass<RedactedStateTimelineElementViewModel> =
            RedactedStateTimelineElementViewModel::class
    }
}

/**
 * Redacted messages stay hidden in the timeline itself, but a reply that references one must not
 * collapse into an empty padded box. Render the (Tacit) reply pill with a single muted line so the
 * quote keeps its context ("replying to <sender>") and states that the original message is gone.
 */
@Composable
private fun <T> DeletedReplyPreview(
    holder: TimelineElementHolderViewModel,
    element: T,
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
) where T : TimelineElementViewModel<*>, T : RedactedTimelineElementViewModel {
    ReferencedMessagePill(
        holder = holder,
        element = element,
        modifier = modifier,
        interactionSource = interactionSource,
    ) {
        val i18n = DI.get<I18n>()
        val message = element.message.collectAsState().value ?: i18n.eventMessageRedactedByUnknown()
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.tacit.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
