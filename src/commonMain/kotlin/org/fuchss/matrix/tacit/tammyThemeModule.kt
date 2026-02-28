package org.fuchss.matrix.tacit

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
    single<ThemeComponents> { TacitThemeComponents() }
}

private class TacitThemeComponents(
    private val delegate: ThemeComponents = ThemeComponentsImpl(),
) : ThemeComponents {
    @Composable
    override fun create(): ComponentStyles = TacitThemeBundle.decorateComponents(delegate.create())
}
