package org.fuchss.matrix.tacit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.common.deriveFromHue
import de.connect2x.trixnity.messenger.compose.view.common.hue
import de.connect2x.trixnity.messenger.compose.view.theme.ComponentStyles

internal object TacitThemeBundle {
    val defaultAccentColor: Color
        get() = tacitDefaultAccent

    fun decorateComponents(base: ComponentStyles): ComponentStyles {
        val commonBanner = base.commonBanner.copy(
            color = tacitCommonBannerBg,
            contentColor = tacitCommonBannerText,
            border = BorderStroke(1.dp, tacitCommonBannerBorder),
            focusedBorder = BorderStroke(1.dp, tacitCommonBannerBorder),
        )
        val warningBanner = base.warningBanner.copy(
            color = tacitWarningBannerBg,
            contentColor = tacitWarningBannerText,
            border = BorderStroke(1.dp, tacitWarningBannerBorder),
            focusedBorder = BorderStroke(1.dp, tacitWarningBannerBorder),
        )
        val errorBanner = base.errorBanner.copy(
            color = tacitErrorBannerBg,
            contentColor = tacitErrorBannerText,
            border = BorderStroke(1.dp, tacitErrorBannerBorder),
            focusedBorder = BorderStroke(1.dp, tacitErrorBannerBorder),
        )
        val dropdownMenu = base.dropdownMenu.copy(
            shape = RoundedCornerShape(14.dp),
            color = tacitPanelHigh,
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
                color = tacitErrorBannerBg,
                contentColor = tacitErrorBannerText,
            ),
        )
    }

    fun createTacitColorScheme(accentColor: Color): ColorScheme {
        updateTacitAccentColor(accentColor)
        val accentHue = accentColor.hue
        val theme = { color: Color -> color.deriveFromHue(accentHue) }
        return darkColorScheme(
            primary = accentColor,
            onPrimary = theme(Color(0xFF06270A)),
            primaryContainer = theme(Color(0xFF2D8D41)),
            onPrimaryContainer = theme(Color(0xFFDFF9DE)),
            secondary = theme(Color(0xFF4FBF73)),
            onSecondary = theme(Color(0xFF041D0D)),
            secondaryContainer = theme(Color(0xFF183A27)),
            onSecondaryContainer = theme(Color(0xFFBFEFCC)),
            tertiary = theme(Color(0xFFA7F29A)),
            onTertiary = theme(Color(0xFF06250A)),
            tertiaryContainer = theme(Color(0xFF2A6A32)),
            onTertiaryContainer = theme(Color(0xFFD1F8C6)),
            error = Color(0xFFFFB4AB),
            errorContainer = Color(0xFF93000A),
            onError = Color(0xFF690005),
            onErrorContainer = Color(0xFFFFDAD6),
            background = tacitShell,
            onBackground = tacitText,
            surface = tacitLayer,
            onSurface = tacitText,
            surfaceVariant = tacitPanel,
            onSurfaceVariant = theme(Color(0xFFD0E5D7)),
            outline = tacitBorder,
            inverseOnSurface = theme(Color(0xFF152419)),
            inverseSurface = theme(Color(0xFFDCEFE1)),
            inversePrimary = theme(Color(0xFF2D8D41)),
            surfaceTint = accentColor,
            outlineVariant = theme(Color(0xFF274435)),
            scrim = Color(0xFF000000),
            surfaceDim = theme(Color(0xFF070D09)),
            surfaceBright = tacitPanelHigh,
            surfaceContainerLowest = theme(Color(0xFF050A07)),
            surfaceContainerLow = theme(Color(0xFF0C1510)),
            surfaceContainer = tacitLayer,
            surfaceContainerHigh = tacitPanel,
            surfaceContainerHighest = tacitPanelHigh,
        )
    }
}
