package org.fuchss.matrix.tacit.readmeshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.messenger.compose.view.Client
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.EscapeKeyPressed
import de.connect2x.trixnity.messenger.compose.view.Platform
import de.connect2x.trixnity.messenger.compose.view.PlatformType
import de.connect2x.trixnity.messenger.compose.view.theme.IsFocusHighlighting
import de.connect2x.trixnity.messenger.compose.view.theme.MessengerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test

/**
 * Dev-only capture of formatted blockquotes (`<blockquote>`, markdown `> …`) next to reply quotes, in a room of its
 * own (the README seed has no blockquote and stays untouched). Skipped unless `TACIT_QUOTE_SCREENSHOTS_DIR` is set;
 * never writes into `.images/`. Run with
 * `TACIT_QUOTE_SCREENSHOTS_DIR=/some/dir ./gradlew generateReadmeScreenshots --tests '*QuoteScreenshots*'`
 * (optional file name prefix: `TACIT_QUOTE_SCREENSHOTS_PREFIX`).
 */
@OptIn(ExperimentalTestApi::class)
class QuoteScreenshots {
    @Test
    fun `capture blockquote screenshots`() {
        val outputDir = System.getenv("TACIT_QUOTE_SCREENSHOTS_DIR")?.let(::File) ?: return
        val prefix = System.getenv("TACIT_QUOTE_SCREENSHOTS_PREFIX") ?: ""
        outputDir.mkdirs()
        Locale.setDefault(Locale.ENGLISH)
        val dataDir = Files.createTempDirectory("tacit-quote-screenshots")
        configureScreenshotLogging(dataDir.toOkioPath())

        tuwunelDocker().use { container ->
            container.start()
            val serverUrl = container.clientServerUrl()
            val users = seedDemoUsers(MatrixHttp(serverUrl))
            val room = seedQuoteRoom(users)

            val app = runBlocking { ScreenshotMessenger.start(dataDir.toOkioPath()) }
            try {
                SkikoComposeUiTest(
                    width = ScreenshotFrame.CONTENT_WIDTH_PX,
                    height = ScreenshotFrame.CONTENT_HEIGHT_PX,
                    effectContext = EmptyCoroutineContext,
                    density = Density(1f),
                ).runTest {
                    mainClock.autoAdvance = false
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(ScreenshotFrame.DENSITY)) {
                            CompositionLocalProvider(
                                Platform provides PlatformType.DESKTOP,
                                DI provides app.messenger.di,
                                IsFocusHighlighting provides false,
                                EscapeKeyPressed provides MutableSharedFlow<Unit>(extraBufferCapacity = 1),
                            ) {
                                MessengerTheme { Client(app.root) }
                            }
                        }
                    }
                    fun pump() {
                        mainClock.advanceTimeByFrame()
                        waitForIdle()
                    }
                    fun settle(millis: Long) {
                        val deadline = System.currentTimeMillis() + millis
                        while (System.currentTimeMillis() < deadline) {
                            pump()
                            Thread.sleep(16)
                        }
                    }
                    fun <T> await(what: String, block: suspend CoroutineScope.() -> T): T {
                        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                        val deferred = scope.async(block = block)
                        val deadline = System.currentTimeMillis() + 120_000
                        try {
                            while (!deferred.isCompleted) {
                                pump()
                                Thread.sleep(16)
                                check(System.currentTimeMillis() < deadline) { "timed out: $what" }
                            }
                            return runBlocking { deferred.await() }
                        } finally {
                            scope.cancel()
                        }
                    }
                    fun waitForText(text: String) {
                        val deadline = System.currentTimeMillis() + 60_000
                        while (onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty()) {
                            check(System.currentTimeMillis() < deadline) { "timed out waiting for '$text'" }
                            pump()
                            Thread.sleep(50)
                        }
                    }
                    fun settleForCapture() {
                        settle(800)
                        onAllNodes(isRoot()).onFirst().performMouseInput {
                            moveTo(Offset(60f, (ScreenshotFrame.CONTENT_HEIGHT_PX - 40).toFloat()))
                        }
                        repeat(4) {
                            val lists = onAllNodes(hasScrollToIndexAction())
                            repeat(lists.fetchSemanticsNodes().size) { i -> runCatching { lists[i].performScrollToIndex(0) } }
                            settle(500)
                        }
                    }
                    fun capture(name: String) {
                        pump()
                        val roots = onAllNodes(isRoot())
                        val image = roots[roots.fetchSemanticsNodes().size - 1].captureToImage()
                        File(outputDir, "$prefix$name.png").writeBytes(ScreenshotFrame.toPng(image))
                        println("SCREENSHOT wrote ${File(outputDir, "$prefix$name.png")}")
                    }

                    settle(300)
                    await("login") { app.login(serverUrl, "tacit", DEMO_PASSWORD) }
                    await("open the quote room") {
                        app.selectRoom(RoomId(room)).also { delay(1_000) }
                    }
                    waitForText("Couldn't agree more")
                    settleForCapture()
                    capture("quotes")
                }
            } finally {
                runBlocking { runCatching { app.multi.closeSuspending() } }
            }
        }
    }
}

/** A "Book Club" room with formatted blockquotes (one nested) and replies to them, so quotes and reply cards sit side by side. */
private fun seedQuoteRoom(users: DemoUsers): String {
    val (tacit, _, mira, ollie, _) = users
    val room = mira.createChat(listOf(tacit.userId, ollie.userId), name = "Book Club")
    tacit.join(room)
    ollie.join(room)

    mira.sendText(room, "Finished chapter three last night 📚")
    ollie.sendReply(room, "Chapter four is even better.", mira.findMessageEventId(room, "chapter three"))
    mira.sendFormatted(
        room,
        body = "> The best way out is always through.\n\nThat line stuck with me all week.",
        html = "<blockquote>\n<p>The best way out is always through.</p>\n</blockquote>\n<p>That line stuck with me all week.</p>\n",
    )
    ollie.sendFormatted(
        room,
        body = "> > Was it Frost?\n> Yes, \"A Servant to Servants\".\n\nLooked it up 🤓",
        html = "<blockquote>\n<blockquote>\n<p>Was it Frost?</p>\n</blockquote>\n<p>Yes, <em>A Servant to Servants</em>.</p>\n</blockquote>\n<p>Looked it up 🤓</p>\n",
    )
    tacit.sendReply(room, "Couldn't agree more, Frost at his best.", mira.findMessageEventId(room, "always through"))
    return room
}

private fun MatrixSession.sendFormatted(roomId: String, body: String, html: String): String = sendMessage(
    roomId,
    buildJsonObject {
        put("msgtype", "m.text")
        put("body", body)
        put("format", "org.matrix.custom.html")
        put("formatted_body", html)
    },
)
