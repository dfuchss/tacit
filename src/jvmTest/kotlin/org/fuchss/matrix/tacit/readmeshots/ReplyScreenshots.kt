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
import de.connect2x.trixnity.core.model.EventId
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
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import org.junit.AssumptionViolatedException

/**
 * Dev-only capture of the reply UI (quote in the timeline, reply bar in the composer) against the seeded demo
 * homeserver. Skipped unless `TACIT_REPLY_SCREENSHOTS_DIR` is set; never writes into `.images/`. Run with
 * `TACIT_REPLY_SCREENSHOTS_DIR=/some/dir ./gradlew generateReadmeScreenshots --tests '*ReplyScreenshots*'`.
 */
@OptIn(ExperimentalTestApi::class)
class ReplyScreenshots {
    private val tempDirs = mutableListOf<java.nio.file.Path>()

    @AfterTest
    fun cleanUpTempDirs() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
        tempDirs.clear()
    }

    @Test
    fun `capture reply screenshots`() {
        val outputDir = System.getenv("TACIT_REPLY_SCREENSHOTS_DIR")?.let(::File)
            ?: throw AssumptionViolatedException("TACIT_REPLY_SCREENSHOTS_DIR is not set")
        val prefix = System.getenv("TACIT_REPLY_SCREENSHOTS_PREFIX") ?: ""
        outputDir.mkdirs()
        Locale.setDefault(Locale.ENGLISH)
        val dataDir = Files.createTempDirectory("tacit-reply-screenshots").also(tempDirs::add)
        configureScreenshotLogging(dataDir.toOkioPath())

        tuwunelDocker().use { container ->
            container.start()
            val serverUrl = container.clientServerUrl()
            val users = seedDemoUsers(MatrixHttp(serverUrl))
            val hike = seedDemoConversations(users)
            val kickoff = users.mira.findMessageEventId(hike, "ridge trail")

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
                    val timeline = await("open the hike room") {
                        app.selectRoom(RoomId(hike)).also { delay(1_000) }
                    }
                    waitForText("Saturday works")
                    waitForText("good camera")
                    settleForCapture()
                    capture("timeline")

                    await("start a reply") { timeline.inputAreaViewModel.replyMessage(RoomId(hike), EventId(kickoff)) }
                    settle(1_500)
                    settleForCapture()
                    capture("composer")
                }
            } finally {
                runBlocking { runCatching { app.multi.closeSuspending() } }
            }
        }
    }
}
