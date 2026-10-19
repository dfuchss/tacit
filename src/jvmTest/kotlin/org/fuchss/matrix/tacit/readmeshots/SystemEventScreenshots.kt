package org.fuchss.matrix.tacit.readmeshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.SkikoComposeUiTest
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toOkioPath
import org.fuchss.matrix.tacit.views.room.timeline.TacitSystemEventGroupTestTag
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test

/**
 * Dev-only capture of the collapsed system-event group in a fresh DM (the same scenario as the README's
 * `chatting.png`): tacit2 opens a DM, tacit accepts and answers. Writes `collapsed.png` and, when a group header
 * is on screen, `expanded.png` after clicking it. Skipped unless `TACIT_SYSEVENT_SCREENSHOTS_DIR` is set; never
 * writes into `.images/`. Run with
 * `TACIT_SYSEVENT_SCREENSHOTS_DIR=/some/dir ./gradlew generateReadmeScreenshots --tests '*SystemEventScreenshots*'`
 * (optional file name prefix: `TACIT_SYSEVENT_SCREENSHOTS_PREFIX`).
 */
@OptIn(ExperimentalTestApi::class)
class SystemEventScreenshots {
    @Test
    fun `capture system event group screenshots`() {
        val outputDir = System.getenv("TACIT_SYSEVENT_SCREENSHOTS_DIR")?.let(::File) ?: return
        val prefix = System.getenv("TACIT_SYSEVENT_SCREENSHOTS_PREFIX") ?: ""
        outputDir.mkdirs()
        Locale.setDefault(Locale.ENGLISH)
        val dataDir = Files.createTempDirectory("tacit-sysevent-screenshots")
        configureScreenshotLogging(dataDir.toOkioPath())
        val escapeKeyPressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        tuwunelDocker().use { container ->
            container.start()
            val serverUrl = container.clientServerUrl()
            val users = seedDemoUsers(MatrixHttp(serverUrl))
            val (tacit, tacit2) = users.tacit to users.tacit2

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
                                EscapeKeyPressed provides escapeKeyPressed,
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
                    fun waitFor(what: String, matcher: SemanticsMatcher) {
                        val deadline = System.currentTimeMillis() + 60_000
                        while (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
                            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
                            pump()
                            Thread.sleep(50)
                        }
                    }
                    fun parkPointer() {
                        onAllNodes(isRoot()).onFirst().performMouseInput {
                            moveTo(Offset(60f, (ScreenshotFrame.CONTENT_HEIGHT_PX - 40).toFloat()))
                        }
                        pump()
                    }
                    fun settleForCapture() {
                        settle(800)
                        parkPointer()
                        val popupDeadline = System.currentTimeMillis() + 6_000
                        do {
                            escapeKeyPressed.tryEmit(Unit)
                            settle(500)
                        } while (onAllNodes(isPopup()).fetchSemanticsNodes().isNotEmpty() && System.currentTimeMillis() < popupDeadline)
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
                        val out = File(outputDir, "$prefix$name.png")
                        out.writeBytes(ScreenshotFrame.toPng(image))
                        println("SCREENSHOT wrote $out")
                    }

                    settle(300)
                    await("login") { app.login(serverUrl, "tacit", DEMO_PASSWORD) }

                    // The same fresh DM as the README's chatting.png: tacit2 invites, tacit accepts.
                    val dmRoomId = tacit2.createRoom(
                        buildJsonObject {
                            put("is_direct", true)
                            put("preset", "trusted_private_chat")
                            put("invite", buildJsonArray { add(JsonPrimitive(tacit.userId)) })
                        }
                    )
                    tacit2.markDirect(tacit.userId, dmRoomId)
                    val dmRoom = RoomId(dmRoomId)
                    await("accept DM invitation") {
                        val element = app.roomElement(dmRoom)
                        element.isInvite.first { it == true }
                        element.acceptInvitation()
                        element.isInvite.first { it == false }
                    }
                    tacit2.setPresence("online")
                    tacit2.sendText(dmRoomId, "Howdy 👋")
                    tacit2.sendText(dmRoomId, "Just installed Tacit")
                    tacit2.sendText(dmRoomId, "Looks neat so far!")
                    await("open DM and send a message") {
                        val timeline = app.selectRoom(dmRoom)
                        delay(1_000)
                        with(app) { sendMessage(timeline.inputAreaViewModel, "Let's chat 😊") }
                    }
                    waitFor("own message", hasText("Let's chat 😊", substring = true))
                    waitFor("tacit2's message", hasText("Howdy", substring = true))
                    settleForCapture()
                    capture("collapsed")

                    val header = hasTestTag(TacitSystemEventGroupTestTag)
                    if (onAllNodes(header).fetchSemanticsNodes().isNotEmpty()) {
                        onAllNodes(header).onFirst().performClick()
                        settle(1_000)
                        settleForCapture()
                        capture("expanded")
                    } else {
                        println("SCREENSHOT no system event group on screen; skipped expanded.png")
                    }
                }
            } finally {
                runBlocking { runCatching { app.multi.closeSuspending() } }
            }
        }
    }
}
