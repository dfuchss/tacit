package org.fuchss.matrix.tacit.views.settings

import androidx.compose.runtime.Immutable
import de.connect2x.trixnity.messenger.compose.view.theme.DefaultSizes

/**
 * Upstream's size limits with higher maxima: upstream stops at 150% display size and 200% font size, which is still
 * small where the toolkit does not apply the system scale itself (e.g. XWayland with fractional scaling, which AWT
 * renders at 1x).
 */
@Immutable
class TacitDefaultSizes : DefaultSizes {
    override val minFontSize: Float = 0.50f
    override val maxFontSize: Float = 3.00f
    override val fontSize: Float = 1.0f

    override val minDisplaySize: Float = 0.5f
    override val maxDisplaySize: Float = 3.0f
    override val displaySize: Float = 1.0f
}
