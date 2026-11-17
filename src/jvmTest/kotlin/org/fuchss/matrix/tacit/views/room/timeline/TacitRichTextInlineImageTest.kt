package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolderImpl
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages
import de.connect2x.trixnity.messenger.i18n.GetSystemLang
import de.connect2x.trixnity.messenger.settings.SettingsStorage
import de.connect2x.trixnity.messenger.util.html.HtmlVisitor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.TimeZone
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class TacitRichTextInlineImageTest {
    private val png: ByteArray = ByteArrayOutputStream()
        .apply { ImageIO.write(BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "png", this) }
        .toByteArray()

    /** Upstream's inline renderer reads its [I18nView] from Koin, like in the app. */
    private val koin = koinApplication {
        modules(
            module {
                single {
                    val storage = object : SettingsStorage {
                        override suspend fun read(): String? = null
                        override suspend fun write(settings: String) {}
                    }
                    I18nView(DefaultLanguages, MatrixMessengerSettingsHolderImpl(storage), GetSystemLang { "en" }, TimeZone.UTC)
                }
            }
        )
    }.koin

    @Composable
    private fun WithDi(content: @Composable () -> Unit) =
        CompositionLocalProvider(DI provides koin) { MaterialTheme { content() } }

    @Test
    fun customEmotesRenderInlineInRunsAndBlockquotes() = runComposeUiTest {
        val requested = CopyOnWriteArrayList<String>()
        val emote = "<img data-mx-emoticon src='mxc://example.org/cat' alt=':cat:' height='32'>"
        val document = HtmlVisitor.process("<blockquote>quoted $emote</blockquote><p>answer $emote</p>")
        setContent {
            WithDi {
                TacitRichTextDisplay(
                    document = document,
                    mentions = null,
                    loadImage = { uri, _, _ -> requested += uri; png },
                )
            }
        }
        waitUntil { onAllNodesWithContentDescription(":cat:").fetchSemanticsNodes().size == 2 }
        assertEquals(setOf("mxc://example.org/cat"), requested.toSet())
    }

    @Test
    fun withoutALoaderTheAltTextStays() = runComposeUiTest {
        val document = HtmlVisitor.process("<p>hi <img data-mx-emoticon src='mxc://example.org/cat' alt=':cat:'></p>")
        setContent { WithDi { TacitRichTextDisplay(document = document, mentions = null) } }
        waitForIdle()
        assertEquals(0, onAllNodesWithContentDescription(":cat:").fetchSemanticsNodes().size)
    }
}
