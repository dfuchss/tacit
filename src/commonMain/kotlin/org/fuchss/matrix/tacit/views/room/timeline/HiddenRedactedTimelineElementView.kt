package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.RedactedTimelineElementView
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.RedactedTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import kotlin.reflect.KClass

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
    ) = Unit

    @Composable
    override fun createReplyInSendMessage(
        holder: TimelineElementHolderViewModel,
        element: RedactedTimelineElementViewModel,
        modifier: Modifier,
        interactionSource: MutableInteractionSource,
    ) = Unit

    @Composable
    override fun getClipEntry(
        holder: BaseTimelineElementHolderViewModel,
        element: RedactedTimelineElementViewModel,
    ): ClipEntry? = null
}
