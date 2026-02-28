package org.fuchss.matrix.tacit.views

import androidx.compose.runtime.Composable
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.ReadMarkerView
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel

class TacitReadMarkerView : ReadMarkerView {
    // Intentionally empty: Tacit hides message read/check indicators.
    @Composable
    override fun create(timelineElementHolderViewModel: BaseTimelineElementHolderViewModel) = Unit
}
