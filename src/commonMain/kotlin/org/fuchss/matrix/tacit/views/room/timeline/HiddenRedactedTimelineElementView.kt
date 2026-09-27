package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.RedactedTimelineElementView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.ReferencedMessagePill
import de.connect2x.trixnity.messenger.i18n.I18n
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.RedactedTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import kotlin.reflect.KClass
import org.fuchss.matrix.tacit.tacitTextMuted

internal object TacitHiddenRedactedTimelineElementView : RedactedTimelineElementView {
    override val supports: KClass<RedactedTimelineElementViewModel> = RedactedTimelineElementViewModel::class

    override suspend fun waitFor(element: RedactedTimelineElementViewModel) = Unit

    override fun isFocusable(): Boolean = false

    @Composable
    override fun createInTimeline(
        holder: BaseTimelineElementHolderViewModel,
        element: RedactedTimelineElementViewModel,
        index: Int,
    ) = Unit

    @Composable
    override fun createAsPreview(
        holder: TimelineElementHolderViewModel,
        element: RedactedTimelineElementViewModel,
        index: Int,
    ) = Unit

    @Composable
    override fun createReplyInTimeline(
        holder: TimelineElementHolderViewModel,
        element: RedactedTimelineElementViewModel,
        modifier: Modifier,
        interactionSource: MutableInteractionSource,
    ) = DeletedReplyPreview(holder, element, modifier, interactionSource)

    @Composable
    override fun createReplyInSendMessage(
        holder: TimelineElementHolderViewModel,
        element: RedactedTimelineElementViewModel,
        modifier: Modifier,
        interactionSource: MutableInteractionSource,
    ) = DeletedReplyPreview(holder, element, modifier, interactionSource)

    @Composable
    override fun getClipEntry(
        holder: BaseTimelineElementHolderViewModel,
        element: RedactedTimelineElementViewModel,
    ): ClipEntry? = null
}

/**
 * Redacted messages stay hidden in the timeline itself, but a reply that references one must not
 * collapse into an empty padded box. Render the upstream reply pill with a single muted line so the
 * quote keeps its context ("replying to <sender>") and states that the original message is gone.
 */
@Composable
private fun DeletedReplyPreview(
    holder: TimelineElementHolderViewModel,
    element: RedactedTimelineElementViewModel,
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
) {
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
            modifier = Modifier.padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = tacitTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
