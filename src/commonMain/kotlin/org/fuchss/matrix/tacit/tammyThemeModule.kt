package org.fuchss.matrix.tacit

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.common.deriveFromHue
import de.connect2x.trixnity.messenger.compose.view.common.hue
import de.connect2x.trixnity.messenger.compose.view.theme.*
import org.koin.dsl.module

fun tammyThemeModule() = module {
    single<DefaultAccentColor> {
        object : DefaultAccentColor {
            override val value = TacitThemeBundle.defaultAccentColor
        }
    }
    single<ThemeLightColorScheme> {
        object : ThemeLightColorScheme {
            override fun create(accentColor: Color): ColorScheme {
                return TacitThemeBundle.createTacitColorScheme(accentColor)
            }
        }
    }
    single<ThemeDarkColorScheme> {
        object : ThemeDarkColorScheme {
            override fun create(accentColor: Color): ColorScheme {
                return TacitThemeBundle.createTacitColorScheme(accentColor)
            }
        }
    }
    single<ThemeLightMessengerColors> { TacitThemeLightMessengerColors() }
    single<ThemeDarkMessengerColors> { TacitThemeDarkMessengerColors() }
    single<ThemeComponents> { TacitThemeComponents() }
    single<Theme> { TacitTheme() }
}

private fun tacitReadableLink(accentColor: Color): Color = Color(0xFF9AF7C2).deriveFromHue(
    hue = accentColor.hue,
    saturation = 0.78f,
    lightness = 0.76f,
)

private class TacitThemeLightMessengerColors(
    private val delegate: ThemeLightMessengerColors = ThemeLightMessengerColorsImpl(),
) : ThemeLightMessengerColors {
    @Composable
    override fun create(accentColor: Color): MessengerColors {
        val base = delegate.create(accentColor)
        val readableLink = tacitReadableLink(accentColor)
        return base.copy(
            link = readableLink,
            linkByMe = readableLink,
            // Upstream draws a solid 3.dp border from this; TacitFlatMessageView paints a soft glow instead.
            mentionBorder = Color.Transparent,
        )
    }
}

private class TacitThemeDarkMessengerColors(
    private val delegate: ThemeDarkMessengerColors = ThemeDarkMessengerColorsImpl(),
) : ThemeDarkMessengerColors {
    @Composable
    override fun create(accentColor: Color): MessengerColors {
        val base = delegate.create(accentColor)
        val readableLink = tacitReadableLink(accentColor)
        return base.copy(
            link = readableLink,
            linkByMe = readableLink,
            // Upstream draws a solid 3.dp border from this; TacitFlatMessageView paints a soft glow instead.
            mentionBorder = Color.Transparent,
        )
    }
}

private class TacitThemeComponents(
    private val delegate: ThemeComponents = ThemeComponentsImpl(),
) : ThemeComponents {
    @Composable
    override fun create(): ComponentStyles =
        TacitThemeBundle.decorateComponents(delegate.create(), MaterialTheme.tacit)
}

/**
 * Wraps upstream's [Theme] and provides [LocalTacitTokens] around it, so the component styles
 * ([TacitThemeComponents]) and all content see the tokens of the current accent.
 *
 * The accent comes from the same place as upstream's default color scheme: the theme settings,
 * falling back to [DefaultAccentColor]. Tokens are rebuilt only when the accent changes.
 */
private class TacitTheme(
    private val delegate: Theme = ThemeImpl(),
) : Theme {
    @OptIn(ExperimentalThemingApi::class)
    @Composable
    override fun create(
        colorScheme: ColorScheme,
        messengerColors: MessengerColors,
        messengerDpConstants: MessengerDpConstants,
        messengerIcons: MessengerIcons,
        shapes: Shapes,
        typography: Typography,
        density: Density,
        componentStyles: ThemeComponents,
        content: @Composable () -> Unit,
    ) {
        val accentColor = CurrentThemeSettings.accentColor ?: DI.get<DefaultAccentColor>().value
        val tokens = remember(accentColor) { TacitTokens.fromAccent(accentColor) }
        CompositionLocalProvider(LocalTacitTokens provides tokens) {
            delegate.create(
                colorScheme,
                messengerColors,
                messengerDpConstants,
                messengerIcons,
                shapes,
                typography,
                density,
                componentStyles,
                content,
            )
        }
    }
}
