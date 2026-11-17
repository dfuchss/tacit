package org.fuchss.matrix.tacit.views

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import de.connect2x.trixnity.messenger.viewmodel.util.EventReactions
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TacitReactionContentTest {
    private val uri = "mxc://example.org/party"

    private fun info(shortcode: String?, image: StateFlow<ByteArray?>?) =
        EventReactions.ByReactionsInfo(reactions = emptySet(), highestStatus = null, shortcode = shortcode, image = image)

    private fun png(): ByteArray = ByteArrayOutputStream()
        .apply { ImageIO.write(BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB), "png", this) }
        .toByteArray()

    @Test
    fun imageReactionsShowTheImageOnceItIsLoaded() = runComposeUiTest {
        val image = MutableStateFlow<ByteArray?>(null)
        setContent { MaterialTheme { TacitReactionContent(uri, info(":party:", image), Color.Unspecified) } }
        onNodeWithText(":party:").assertExists()
        image.value = png()
        waitUntil { onAllNodesWithContentDescription(":party:").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription(":party:").assertExists()
        onNodeWithText(":party:").assertDoesNotExist()
    }

    @Test
    fun undecodableImagesWithoutShortcodeFallBackToTheKey() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TacitReactionContent(uri, info(null, MutableStateFlow(byteArrayOf(1, 2, 3))), Color.Unspecified)
            }
        }
        waitForIdle()
        onNodeWithText(uri).assertExists()
    }

    @Test
    fun emojiReactionsStayText() = runComposeUiTest {
        setContent { MaterialTheme { TacitReactionContent("🎉", info(":tada:", null), Color.Unspecified) } }
        onNodeWithText("🎉").assertExists()
    }
}
