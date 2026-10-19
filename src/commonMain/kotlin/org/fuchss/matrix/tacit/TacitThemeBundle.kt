package org.fuchss.matrix.tacit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
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
            border = BorderStroke(1.dp, tokens.border),
            focusedBorder = BorderStroke(1.dp, tokens.border),
        )
        val warningBanner = base.warningBanner.copy(
            color = tokens.warningBg,
            contentColor = tokens.warningText,
            border = BorderStroke(1.dp, tokens.warningBorder),
            focusedBorder = BorderStroke(1.dp, tokens.warningBorder),
        )
        val errorBanner = base.errorBanner.copy(
            color = tokens.errorBg,
            contentColor = tokens.errorText,
            border = BorderStroke(1.dp, tokens.errorBorder),
            focusedBorder = BorderStroke(1.dp, tokens.errorBorder),
        )
        val dropdownMenu = base.dropdownMenu.copy(
            shape = TacitShapes.card,
            color = tokens.surfaceAlt,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, tokens.border),
            focusedBorder = BorderStroke(1.dp, tokens.accent),
            contentPadding = PaddingValues(vertical = 4.dp),
        )
        val dropdownMenuItem = base.dropdownMenuItem.copy(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            focusedBorder = null,
        )
        return base.copy(
            errorBanner = errorBanner,
            warningBanner = warningBanner,
            commonBanner = commonBanner,
            dropdownMenu = dropdownMenu,
            dropdownMenuItem = dropdownMenuItem,
            messageBubbleError = base.messageBubbleError.copy(
                color = tokens.errorBg,
                contentColor = tokens.errorText,
            ),
        )
    }

    fun createTacitColorScheme(accentColor: Color): ColorScheme {
        val tokens = TacitTokens.fromAccent(accentColor)
        // `primary` & co. use the raw accent; the soft containers use the hue-derived token accent.
        val onAccent = tokens.onAccent(accentColor)
        val accentSoft = tokens.accent(0.22f)
        val accentSoftStrong = tokens.accent(0.35f)
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
            scrim = tokens.background.copy(alpha = 0.65f),
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
