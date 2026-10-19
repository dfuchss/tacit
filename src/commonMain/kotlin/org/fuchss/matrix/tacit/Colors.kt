package org.fuchss.matrix.tacit

import org.fuchss.matrix.tacit.ui.TacitAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import de.connect2x.trixnity.messenger.compose.view.common.contrastByLuminance
import de.connect2x.trixnity.messenger.compose.view.common.deriveFromHue
import de.connect2x.trixnity.messenger.compose.view.common.hue

internal val tacitDefaultAccent: Color = Color(0xFF3BB2FA)

// Base palette: accent + ~5 core colors, re-tinted to the hue of the chosen accent.
private val baseBackground = Color(0xFF0B120E)
private val baseSurface = Color(0xFF122019)
private val baseSurfaceAlt = Color(0xFF1A2B22)
private val baseBorder = Color(0xFF294237)
private val baseText = Color(0xFFEAF7ED)
private val baseTextMuted = Color(0xFFA6C1B2)
private val baseWarning = Color(0xFFE6A24E)
private val baseError = Color(0xFFE56A6A)

/**
 * Tacit's derived palette for one accent color. Build it with [TacitTokens.fromAccent]; read it in
 * composition through [MaterialTheme.tacit], which [TacitTheme] provides via [LocalTacitTokens].
 */
@Immutable
internal data class TacitTokens(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val warning: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val warningText: Color,
    val error: Color,
    val errorBg: Color,
    val errorBorder: Color,
    val errorText: Color,
) {
    /** Pure white content (text, icons, indicators) drawn on accent-tinted fills; independent of the hue. */
    val onAccentFixed: Color get() = Color.White

    fun accent(alpha: Float): Color = accent.copy(alpha = alpha)

    fun onAccent(color: Color = accent): Color = color.contrastByLuminance(brightColor = text, darkColor = background)

    companion object {
        /** Only the hue of [accentColor] matters: the palette is the base palette shifted to that hue. */
        fun fromAccent(accentColor: Color): TacitTokens {
            val hue = accentColor.hue
            val background = baseBackground.deriveFromHue(hue)
            val text = baseText.deriveFromHue(hue)
            return TacitTokens(
                accent = tacitDefaultAccent.deriveFromHue(hue),
                background = background,
                surface = baseSurface.deriveFromHue(hue),
                surfaceAlt = baseSurfaceAlt.deriveFromHue(hue),
                border = baseBorder.deriveFromHue(hue),
                text = text,
                textMuted = baseTextMuted.deriveFromHue(hue),
                warning = baseWarning,
                warningBg = baseWarning.copy(alpha = 0.20f),
                warningBorder = baseWarning.copy(alpha = TacitAlpha.borderEmphasis),
                warningText = baseWarning.contrastByLuminance(brightColor = text, darkColor = background),
                error = baseError,
                errorBg = baseError.copy(alpha = 0.20f),
                errorBorder = baseError.copy(alpha = 0.70f),
                errorText = baseError.contrastByLuminance(brightColor = text, darkColor = background),
            )
        }

        val Default: TacitTokens = fromAccent(tacitDefaultAccent)
    }
}

internal val LocalTacitTokens = staticCompositionLocalOf { TacitTokens.Default }

internal val MaterialTheme.tacit: TacitTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalTacitTokens.current
