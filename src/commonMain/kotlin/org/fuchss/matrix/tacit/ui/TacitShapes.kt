package org.fuchss.matrix.tacit.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal object TacitShapes {
    val appShell: Shape = RoundedCornerShape(28.dp)
    val rail: Shape = RoundedCornerShape(24.dp)
    val pane: Shape = RoundedCornerShape(24.dp)
    val paneInner: Shape = RoundedCornerShape(20.dp)
    val section: Shape = RoundedCornerShape(18.dp)
    val card: Shape = RoundedCornerShape(16.dp)
    val control: Shape = RoundedCornerShape(14.dp)
    val compact: Shape = RoundedCornerShape(12.dp)
    val pill: Shape = RoundedCornerShape(999.dp)
    val selectedPill: Shape = RoundedCornerShape(16.dp)
    val circle: Shape = CircleShape
}
