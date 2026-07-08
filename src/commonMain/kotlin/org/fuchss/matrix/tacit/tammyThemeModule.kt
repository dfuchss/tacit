package org.fuchss.matrix.tacit

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
            mentionBorder = accentColor,
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
            mentionBorder = accentColor,
        )
    }
}

private class TacitThemeComponents(
    private val delegate: ThemeComponents = ThemeComponentsImpl(),
) : ThemeComponents {
    @Composable
    override fun create(): ComponentStyles = TacitThemeBundle.decorateComponents(delegate.create())
}
