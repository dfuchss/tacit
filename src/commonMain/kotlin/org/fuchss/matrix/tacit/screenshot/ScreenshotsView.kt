package org.fuchss.matrix.tacit.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.fuchss.matrix.tacit.tammyThemeModule
import de.connect2x.trixnity.messenger.MatrixMessengerConfiguration
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.EscapeKeyPressed
import de.connect2x.trixnity.messenger.compose.view.Platform
import de.connect2x.trixnity.messenger.compose.view.PlatformType
import de.connect2x.trixnity.messenger.compose.view.composeViewModule
import de.connect2x.trixnity.messenger.compose.view.root.Main
import de.connect2x.trixnity.messenger.compose.view.theme.IsFocusHighlighting
import de.connect2x.trixnity.messenger.compose.view.theme.MessengerTheme
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedSurface
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages
import de.connect2x.trixnity.messenger.i18n.I18n
import de.connect2x.trixnity.messenger.i18n.Languages
import de.connect2x.trixnity.messenger.i18n.platformGetSystemLangModule
import de.connect2x.trixnity.messenger.platformMatrixMessengerSettingsHolderModule
import de.connect2x.trixnity.messenger.util.RootPath
import de.connect2x.trixnity.messenger.util.UriCaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.koinApplication
import org.koin.dsl.module

// FIXME go to room is missing (isRoomShown is no longer available on mainViewModel)
@Composable
fun ScreenshotView(locale: Locale, showRoom: MutableStateFlow<Boolean>) {
    var initFinished by remember { mutableStateOf(true) }

    val di = remember {
        koinApplication {
            modules(
                // TODO this needs to be removed and fixed, as there is no MatrixMessengerSettingsHolderImpl at MultiMessenger level!
                platformMatrixMessengerSettingsHolderModule(),
                // TODO there should be a more clean way for I18n
                platformGetSystemLangModule(),
                module {
                    single<Languages> { DefaultLanguages }
                    single<I18n> { object : I18n(get(), get(), get(), get()) {} }
                    single<TimeZone> { TimeZone.UTC }
                    single<FileSystem> { FakeFileSystem() }
                    single<RootPath> {
                        RootPath("/app".toPath())
                    }
                    single<MatrixMessengerConfiguration> {
                        MatrixMessengerConfiguration()
                    }
                    single<UriCaller> {
                        UriCaller { _, _ -> }
                    }
                },
                composeViewModule(MatrixMessengerConfiguration()),
                tammyThemeModule(),
            )
        }.koin
    }
    LaunchedEffect(Unit) {
        di.get<MatrixMessengerSettingsHolder>().init()
        initFinished = true
    }

    if (initFinished) {
        val mainViewModel = remember { MockMainViewModel(locale, showRoom) }
        CompositionLocalProvider(
            Platform provides PlatformType.ANDROID,
            DI provides di,
            IsFocusHighlighting provides false,
            EscapeKeyPressed provides flow { },
        ) {
            MessengerTheme {
                ThemedSurface(
                    style = MaterialTheme.components.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    Main(mainViewModel)
                }
            }
        }
    } else Text("loading")
}