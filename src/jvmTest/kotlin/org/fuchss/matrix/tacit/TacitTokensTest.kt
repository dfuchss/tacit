package org.fuchss.matrix.tacit

import androidx.compose.ui.graphics.Color
import de.connect2x.trixnity.messenger.compose.view.common.contrastByLuminance
import de.connect2x.trixnity.messenger.compose.view.common.deriveFromHue
import de.connect2x.trixnity.messenger.compose.view.common.hue
import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitShapes
import kotlin.test.Test
import kotlin.test.assertEquals

class TacitTokensTest {
    private val accents = listOf(
        Color(0xFF3BB2FA), // default
        Color(0xFFE53935),
        Color(0xFF43A047),
        Color(0xFF8E24AA),
        Color(0xFFFFEB3B),
    )

    /** The palette as the former global getters (keyed on a mutable `tacitAccentHue`) computed it. */
    private fun legacyTokens(accentColor: Color): TacitTokens {
        val hue = accentColor.hue
        fun tacit(color: Color) = color.deriveFromHue(hue)
        val background = tacit(Color(0xFF0B120E))
        val text = tacit(Color(0xFFEAF7ED))
        val warning = Color(0xFFE6A24E)
        val error = Color(0xFFE56A6A)
        return TacitTokens(
            accent = tacit(Color(0xFF3BB2FA)),
            background = background,
            surface = tacit(Color(0xFF122019)),
            surfaceAlt = tacit(Color(0xFF1A2B22)),
            border = tacit(Color(0xFF294237)),
            text = text,
            textMuted = tacit(Color(0xFFA6C1B2)),
            warning = warning,
            warningBg = warning.copy(alpha = 0.20f),
            warningBorder = warning.copy(alpha = 0.65f),
            warningText = warning.contrastByLuminance(brightColor = text, darkColor = background),
            error = error,
            errorBg = error.copy(alpha = 0.20f),
            errorBorder = error.copy(alpha = 0.70f),
            errorText = error.contrastByLuminance(brightColor = text, darkColor = background),
        )
    }

    @Test
    fun tokensMatchTheFormerGlobalGetters() {
        for (accent in accents) {
            val tokens = TacitTokens.fromAccent(accent)
            assertEquals(legacyTokens(accent), tokens, "accent $accent")
            assertEquals(tokens.accent.copy(alpha = 0.35f), tokens.accent(0.35f))
            assertEquals(
                accent.contrastByLuminance(brightColor = tokens.text, darkColor = tokens.background),
                tokens.onAccent(accent),
            )
            assertEquals(tokens.onAccent(tokens.accent), tokens.onAccent())
        }
    }

    @Test
    fun defaultTokensUseTheDefaultAccent() {
        val tokens = TacitTokens.Default
        assertEquals(TacitTokens.fromAccent(Color(0xFF3BB2FA)), tokens)
        assertEquals(Color(0xFFE6A24E), tokens.warning)
        assertEquals(Color(0xFFE56A6A), tokens.error)
        assertColorClose(Color(0xFF3BB2FA), tokens.accent)
    }

    @Test
    fun colorSchemeUsesTheTokensOfItsAccent() {
        for (accent in accents) {
            val tokens = TacitTokens.fromAccent(accent)
            val scheme = TacitThemeBundle.createTacitColorScheme(accent)
            assertEquals(accent, scheme.primary)
            assertEquals(tokens.onAccent(accent), scheme.onPrimary)
            assertEquals(tokens.accent(0.35f), scheme.primaryContainer)
            assertEquals(tokens.accent(0.22f), scheme.secondaryContainer)
            assertEquals(tokens.background, scheme.background)
            assertEquals(tokens.surface, scheme.surface)
            assertEquals(tokens.surfaceAlt, scheme.surfaceVariant)
            assertEquals(tokens.textMuted, scheme.onSurfaceVariant)
            assertEquals(tokens.border, scheme.outline)
            assertEquals(tokens.errorBg, scheme.errorContainer)
        }
    }

    @Test
    fun namedTokensKeepTheirFormerLiteralValues() {
        assertEquals(0.82f, TacitAlpha.quotedText)
        assertEquals(0.8f, TacitAlpha.focusRing)
        assertEquals(0.65f, TacitAlpha.borderEmphasis)
        assertEquals(0.45f, TacitAlpha.borderSubtle)
        assertEquals(0.45f, TacitAlpha.disabled)
        assertEquals(0.35f, TacitAlpha.accentSoftStrong)
        assertEquals(0.25f, TacitAlpha.accentFill)
        assertEquals(0.22f, TacitAlpha.accentSoft)
        assertEquals(16f, TacitShapes.cardRadius.value)
        assertEquals(Color.White, TacitTokens.Default.onAccentFixed)
    }

    private fun assertColorClose(expected: Color, actual: Color) {
        val tolerance = 1.5f / 255f
        val ok = kotlin.math.abs(expected.red - actual.red) <= tolerance &&
            kotlin.math.abs(expected.green - actual.green) <= tolerance &&
            kotlin.math.abs(expected.blue - actual.blue) <= tolerance
        kotlin.test.assertTrue(ok, "expected $expected, got $actual")
    }
}
