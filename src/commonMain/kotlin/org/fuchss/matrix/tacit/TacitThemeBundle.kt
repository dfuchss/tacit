package org.fuchss.matrix.tacit

import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.theme.ComponentStyles
import org.fuchss.matrix.tacit.ui.TacitShapes

internal object TacitThemeBundle {
    val defaultAccentColor: Color
        get() = tacitDefaultAccent

    fun decorateComponents(base: ComponentStyles, tokens: TacitTokens): ComponentStyles {
        val commonBanner = base.commonBanner.copy(
            color = tokens.surfaceAlt,
            contentColor = tokens.text,
            border = BorderStroke(TacitSizes.borderWidth, tokens.border),
            focusedBorder = BorderStroke(TacitSizes.borderWidth, tokens.border),
        )
        val warningBanner = base.warningBanner.copy(
            color = tokens.warningBg,
            contentColor = tokens.warningText,
            border = BorderStroke(TacitSizes.borderWidth, tokens.warningBorder),
            focusedBorder = BorderStroke(TacitSizes.borderWidth, tokens.warningBorder),
        )
        val errorBanner = base.errorBanner.copy(
            color = tokens.errorBg,
            contentColor = tokens.errorText,
            border = BorderStroke(TacitSizes.borderWidth, tokens.errorBorder),
            focusedBorder = BorderStroke(TacitSizes.borderWidth, tokens.errorBorder),
        )
        val dropdownMenu = base.dropdownMenu.copy(
            shape = TacitShapes.card,
            color = tokens.surfaceAlt,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(TacitSizes.borderWidth, tokens.border),
            focusedBorder = BorderStroke(TacitSizes.borderWidth, tokens.accent),
            contentPadding = PaddingValues(vertical = 4.dp),
        )
        // Upstream's context menu (ThemedActionMenu: anchored dropdown and right-click popup) draws a hairline
        // in DarkGray or LightGray depending on ThemeSettings.isDarkMode(), i.e. on the system appearance.
        // Tacit is always dark: keep the dark hairline. A wider border is upstream's focus ring; leave it.
        val contextMenu = base.contextMenu.copy(border = darkContextMenuBorder(base.contextMenu.border))
        val dropdownMenuItem = base.dropdownMenuItem.copy(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            focusedBorder = null,
        )
        return base.copy(
            errorBanner = errorBanner,
            warningBanner = warningBanner,
            commonBanner = commonBanner,
            dropdownMenu = dropdownMenu,
            contextMenu = contextMenu,
            dropdownMenuItem = dropdownMenuItem,
            messageBubbleError = base.messageBubbleError.copy(
                color = tokens.errorBg,
                contentColor = tokens.errorText,
            ),
        )
    }

    /** Upstream's resting context menu hairline pinned to its dark-mode colour; anything else is kept. */
    fun darkContextMenuBorder(border: BorderStroke?): BorderStroke? =
        if (border != null && border.width == Dp.Hairline) BorderStroke(Dp.Hairline, Color.DarkGray) else border

    fun createTacitColorScheme(accentColor: Color): ColorScheme {
        val tokens = TacitTokens.fromAccent(accentColor)
        // `primary` & co. use the raw accent; the soft containers use the hue-derived token accent.
        val onAccent = tokens.onAccent(accentColor)
        val accentSoft = tokens.accent(TacitAlpha.accentSoft)
        val accentSoftStrong = tokens.accent(TacitAlpha.accentSoftStrong)
        return darkColorScheme(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = accentSoftStrong,
            onPrimaryContainer = tokens.text,
            secondary = accentColor,
            onSecondary = onAccent,
            secondaryContainer = accentSoft,
            onSecondaryContainer = tokens.text,
            tertiary = accentColor,
            onTertiary = onAccent,
            tertiaryContainer = accentSoft,
            onTertiaryContainer = tokens.text,
            error = tokens.error,
            errorContainer = tokens.errorBg,
            onError = tokens.onAccent(tokens.error),
            onErrorContainer = tokens.errorText,
            background = tokens.background,
            onBackground = tokens.text,
            surface = tokens.surface,
            onSurface = tokens.text,
            surfaceVariant = tokens.surfaceAlt,
            onSurfaceVariant = tokens.textMuted,
            outline = tokens.border,
            inverseOnSurface = tokens.background,
            inverseSurface = tokens.text,
            inversePrimary = accentColor,
            surfaceTint = accentColor,
            outlineVariant = tokens.border,
            scrim = tokens.background.copy(alpha = TacitAlpha.borderEmphasis),
            surfaceDim = tokens.background,
            surfaceBright = tokens.surfaceAlt,
            surfaceContainerLowest = tokens.background,
            surfaceContainerLow = tokens.surface,
            surfaceContainer = tokens.surface,
            surfaceContainerHigh = tokens.surface,
            surfaceContainerHighest = tokens.surfaceAlt,
        )
    }
}
