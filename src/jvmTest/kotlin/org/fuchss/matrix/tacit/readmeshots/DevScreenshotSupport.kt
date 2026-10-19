package org.fuchss.matrix.tacit.readmeshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.AssumptionViolatedException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest

/** The offscreen compose harness all screenshot drivers render into. */
@OptIn(ExperimentalTestApi::class)
internal fun newScreenshotHarness(): SkikoComposeUiTest = SkikoComposeUiTest(
    width = ScreenshotFrame.CONTENT_WIDTH_PX,
    height = ScreenshotFrame.CONTENT_HEIGHT_PX,
    effectContext = EmptyCoroutineContext,
    density = Density(1f),
)

/** Hosts [content] (by default the messenger client) with the desktop locals the screenshots are rendered with. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.setScreenshotContent(
    app: ScreenshotMessenger,
    escapeKeyPressed: MutableSharedFlow<Unit>,
    content: @Composable () -> Unit = { MessengerTheme { Client(app.root) } },
) {
    setContent {
        CompositionLocalProvider(LocalDensity provides Density(ScreenshotFrame.DENSITY)) {
            CompositionLocalProvider(
                Platform provides PlatformType.DESKTOP,
                DI provides app.messenger.di,
                IsFocusHighlighting provides false,
                EscapeKeyPressed provides escapeKeyPressed,
                content = content,
            )
        }
    }
}

/** Neutral spot (the footer strip) the pointer is parked on before a capture so no hover state leaks into it. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.parkPointerOnFooter() {
    onAllNodes(isRoot()).onFirst().performMouseInput {
        moveTo(Offset(60f, (ScreenshotFrame.CONTENT_HEIGHT_PX - 40).toFloat()))
    }
}

/** Anchors every lazy list at its start, so the (reversed) timeline sits exactly on its newest item. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteractionCollection.pinToStart() {
    val nodes = fetchSemanticsNodes()
    repeat(nodes.size) { i -> runCatching { this[i].performScrollToIndex(0) } } // empty lists throw
}

/** What a dev-only screenshot driver gets once the demo homeserver is seeded and the messenger started. */
internal class DevScreenshotSession<S>(
    val app: ScreenshotMessenger,
    val serverUrl: String,
    val users: DemoUsers,
    val seeded: S,
)

/**
 * Driver for the dev-only screenshot captures (reply, quote, system events): renders frames while waiting and
 * writes PNGs into [outputDir] as `<prefix><name>.png`.
 */
@OptIn(ExperimentalTestApi::class)
internal class DevScreenshotUi(
    val test: ComposeUiTest,
    private val outputDir: File,
    private val prefix: String,
    private val escapeKeyPressed: MutableSharedFlow<Unit>,
) {
    fun onAllNodes(matcher: SemanticsMatcher): SemanticsNodeInteractionCollection = test.onAllNodes(matcher)

    fun pump() {
        test.mainClock.advanceTimeByFrame()
        test.waitForIdle()
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
        while (test.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            pump()
            Thread.sleep(50)
        }
    }

    fun waitForText(text: String) = waitFor("'$text'", hasText(text, substring = true))

    /** [dismissPopups] additionally presses Escape until no hover tooltip or other popup is left. */
    fun settleForCapture(dismissPopups: Boolean = false) {
        settle(800)
        test.parkPointerOnFooter()
        pump()
        if (dismissPopups) {
            val popupDeadline = System.currentTimeMillis() + 6_000
            do {
                escapeKeyPressed.tryEmit(Unit)
                settle(500)
            } while (test.onAllNodes(isPopup()).fetchSemanticsNodes().isNotEmpty() && System.currentTimeMillis() < popupDeadline)
        }
        repeat(4) {
            test.onAllNodes(hasScrollToIndexAction()).pinToStart()
            settle(500)
        }
    }

    fun capture(name: String) {
        pump()
        val roots = test.onAllNodes(isRoot())
        val image = roots[roots.fetchSemanticsNodes().size - 1].captureToImage()
        val out = File(outputDir, "$prefix$name.png")
        out.writeBytes(ScreenshotFrame.toPng(image))
        println("SCREENSHOT wrote $out")
    }
}

/**
 * Base of the dev-only screenshot captures. Each is skipped unless `TACIT_<[envName]>_SCREENSHOTS_DIR` is set
 * (optional file name prefix: `TACIT_<[envName]>_SCREENSHOTS_PREFIX`) and never writes into `.images/`.
 */
@OptIn(ExperimentalTestApi::class)
abstract class DevScreenshotTest(private val envName: String, private val tempDirName: String) {
    private val tempDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanUpTempDirs() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
        tempDirs.clear()
    }

    /**
     * Starts the demo homeserver and messenger, runs [seed] against the seeded users, then renders the client and
     * runs [body] against it.
     */
    internal fun <S> runDevScreenshots(seed: (DemoUsers) -> S, body: DevScreenshotUi.(DevScreenshotSession<S>) -> Unit) {
        val envVar = "TACIT_${envName}_SCREENSHOTS_DIR"
        val outputDir = System.getenv(envVar)?.let(::File)
            ?: throw AssumptionViolatedException("$envVar is not set")
        val prefix = System.getenv("TACIT_${envName}_SCREENSHOTS_PREFIX") ?: ""
        outputDir.mkdirs()
        Locale.setDefault(Locale.ENGLISH)
        val dataDir = Files.createTempDirectory(tempDirName).also(tempDirs::add)
        configureScreenshotLogging(dataDir.toOkioPath())
        val escapeKeyPressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        tuwunelDocker().use { container ->
            container.start()
            val serverUrl = container.clientServerUrl()
            val users = seedDemoUsers(MatrixHttp(serverUrl))
            val seeded = seed(users)

            val app = runBlocking { ScreenshotMessenger.start(dataDir.toOkioPath()) }
            try {
                newScreenshotHarness().runTest {
                    mainClock.autoAdvance = false
                    setScreenshotContent(app, escapeKeyPressed)
                    DevScreenshotUi(this, outputDir, prefix, escapeKeyPressed)
                        .body(DevScreenshotSession(app, serverUrl, users, seeded))
                }
            } finally {
                runBlocking { runCatching { app.multi.closeSuspending() } }
            }
        }
    }
}
