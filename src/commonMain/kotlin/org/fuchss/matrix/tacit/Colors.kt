package org.fuchss.matrix.tacit

import androidx.compose.ui.graphics.Color
import de.connect2x.trixnity.messenger.compose.view.common.contrastByLuminance
import de.connect2x.trixnity.messenger.compose.view.common.deriveFromHue
import de.connect2x.trixnity.messenger.compose.view.common.hue

private val tacitDefaultAccentColor = Color(0xFF3BB2FA)
private var tacitAccentHue = tacitDefaultAccentColor.hue

internal fun updateTacitAccentColor(color: Color) {
    tacitAccentHue = color.hue
}

private fun Color.tacitize(): Color = deriveFromHue(tacitAccentHue)
private fun tacit(color: Color): Color = color.tacitize()

// Base palette: accent + ~5 core colors.
internal val tacitDefaultAccent: Color
    get() = tacitDefaultAccentColor
internal val accentColor: Color
    get() = tacit(tacitDefaultAccentColor)

internal val tacitBackground: Color
    get() = tacit(Color(0xFF0B120E))
internal val tacitSurface: Color
    get() = tacit(Color(0xFF122019))
internal val tacitSurfaceAlt: Color
    get() = tacit(Color(0xFF1A2B22))
internal val tacitBorder: Color
    get() = tacit(Color(0xFF294237))
internal val tacitText: Color
    get() = tacit(Color(0xFFEAF7ED))
internal val tacitTextMuted: Color
    get() = tacit(Color(0xFFA6C1B2))

internal val tacitWarning: Color
    get() = Color(0xFFE6A24E)
internal val tacitWarningBg: Color
    get() = tacitWarning.copy(alpha = 0.20f)
internal val tacitWarningBorder: Color
    get() = tacitWarning.copy(alpha = 0.65f)
internal val tacitWarningText: Color
    get() = tacitWarning.contrastByLuminance(brightColor = tacitText, darkColor = tacitBackground)

internal val tacitError: Color
    get() = Color(0xFFE56A6A)
internal val tacitErrorBg: Color
    get() = tacitError.copy(alpha = 0.20f)
internal val tacitErrorBorder: Color
    get() = tacitError.copy(alpha = 0.70f)
internal val tacitErrorText: Color
    get() = tacitError.contrastByLuminance(brightColor = tacitText, darkColor = tacitBackground)

internal fun tacitAccent(alpha: Float): Color = accentColor.copy(alpha = alpha)
internal fun tacitOnAccent(color: Color = accentColor): Color = color.contrastByLuminance(
    brightColor = tacitText,
    darkColor = tacitBackground,
)
