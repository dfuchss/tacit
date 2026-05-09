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

    fun decorateComponents(base: ComponentStyles): ComponentStyles {
        val commonBanner = base.commonBanner.copy(
            color = tacitSurfaceAlt,
            contentColor = tacitText,
            border = BorderStroke(1.dp, tacitBorder),
            focusedBorder = BorderStroke(1.dp, tacitBorder),
        )
        val warningBanner = base.warningBanner.copy(
            color = tacitWarningBg,
            contentColor = tacitWarningText,
            border = BorderStroke(1.dp, tacitWarningBorder),
            focusedBorder = BorderStroke(1.dp, tacitWarningBorder),
        )
        val errorBanner = base.errorBanner.copy(
            color = tacitErrorBg,
            contentColor = tacitErrorText,
            border = BorderStroke(1.dp, tacitErrorBorder),
            focusedBorder = BorderStroke(1.dp, tacitErrorBorder),
        )
        val dropdownMenu = base.dropdownMenu.copy(
            shape = TacitShapes.card,
            color = tacitSurfaceAlt,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, tacitBorder),
            focusedBorder = BorderStroke(1.dp, accentColor),
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
                color = tacitErrorBg,
                contentColor = tacitErrorText,
            ),
        )
    }

    fun createTacitColorScheme(accentColor: Color): ColorScheme {
        updateTacitAccentColor(accentColor)
        val onAccent = tacitOnAccent(accentColor)
        val accentSoft = tacitAccent(0.22f)
        val accentSoftStrong = tacitAccent(0.35f)
        return darkColorScheme(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = accentSoftStrong,
            onPrimaryContainer = tacitText,
            secondary = accentColor,
            onSecondary = onAccent,
            secondaryContainer = accentSoft,
            onSecondaryContainer = tacitText,
            tertiary = accentColor,
            onTertiary = onAccent,
            tertiaryContainer = accentSoft,
            onTertiaryContainer = tacitText,
            error = tacitError,
            errorContainer = tacitErrorBg,
            onError = tacitOnAccent(tacitError),
            onErrorContainer = tacitErrorText,
            background = tacitBackground,
            onBackground = tacitText,
            surface = tacitSurface,
            onSurface = tacitText,
            surfaceVariant = tacitSurfaceAlt,
            onSurfaceVariant = tacitTextMuted,
            outline = tacitBorder,
            inverseOnSurface = tacitBackground,
            inverseSurface = tacitText,
            inversePrimary = accentColor,
            surfaceTint = accentColor,
            outlineVariant = tacitBorder,
            scrim = tacitBackground.copy(alpha = 0.65f),
            surfaceDim = tacitBackground,
            surfaceBright = tacitSurfaceAlt,
            surfaceContainerLowest = tacitBackground,
            surfaceContainerLow = tacitSurface,
            surfaceContainer = tacitSurface,
            surfaceContainerHigh = tacitSurface,
            surfaceContainerHighest = tacitSurfaceAlt,
        )
    }
}
