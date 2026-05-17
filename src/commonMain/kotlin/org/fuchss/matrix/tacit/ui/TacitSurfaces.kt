package org.fuchss.matrix.tacit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.fuchss.matrix.tacit.tacitBorder
import org.fuchss.matrix.tacit.tacitSurface
import org.fuchss.matrix.tacit.tacitSurfaceAlt

@Composable
internal fun TacitPaneSurface(
    modifier: Modifier = Modifier,
    shape: Shape = TacitShapes.pane,
    innerShape: Shape = TacitShapes.paneInner,
    backgroundColor: Color = tacitSurface,
    borderColor: Color = tacitBorder,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(TacitSpacing.paneFramePadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(innerShape)
                .background(backgroundColor)
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
internal fun TacitCardSurface(
    modifier: Modifier = Modifier,
    shape: Shape = TacitShapes.card,
    backgroundColor: Color = tacitSurfaceAlt,
    borderColor: Color = tacitBorder,
    contentPadding: PaddingValues = PaddingValues(TacitSpacing.sectionPadding),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content,
    )
}
