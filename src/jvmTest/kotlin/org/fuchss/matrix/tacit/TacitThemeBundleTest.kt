package org.fuchss.matrix.tacit

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class TacitThemeBundleTest {
    @Test
    fun `light mode context menu hairline becomes the dark one`() {
        val pinned = TacitThemeBundle.darkContextMenuBorder(BorderStroke(Dp.Hairline, Color.LightGray))
        assertEquals(BorderStroke(Dp.Hairline, Color.DarkGray), pinned)
    }

    @Test
    fun `dark hairline stays`() {
        assertEquals(BorderStroke(Dp.Hairline, Color.DarkGray), TacitThemeBundle.darkContextMenuBorder(BorderStroke(Dp.Hairline, Color.DarkGray)))
    }

    @Test
    fun `focus ring and missing border are kept`() {
        val focusRing = BorderStroke(2.dp, Color.White)
        assertSame(focusRing, TacitThemeBundle.darkContextMenuBorder(focusRing))
        assertNull(TacitThemeBundle.darkContextMenuBorder(null))
    }
}
