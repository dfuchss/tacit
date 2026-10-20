package org.fuchss.matrix.tacit.readmeshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fuchss.matrix.tacit.update.TacitUpdateCard
import org.fuchss.matrix.tacit.update.TacitUpdateState
import org.fuchss.matrix.tacit.viewmodel.settings.TacitMarkAllAsRead
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextInput
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.messenger.compose.view.Client
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.theme.MessengerTheme
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail

/**
 * Regenerates the README screenshots in `.images/` from scratch:
 *  1. boots a throwaway tuwunel homeserver in Docker (Testcontainers),
 *  2. registers two demo users and seeds rooms/messages over the client-server API,
 *  3. logs the REAL Tacit desktop UI (same DI/config as `Main.kt`) into it, headless,
 *  4. drives the UI to each screen (dialogs are opened by clicking the real buttons), captures it,
 *  5. overwrites the PNGs in place, edge to edge and opaque (the consumer adds any border/shadow).
 *
 * Doubles as an end-to-end smoke test of the desktop client against a real server. It is excluded from
 * `jvmTest`/`check`; run `scripts/screenshots.sh` or `./gradlew generateReadmeScreenshots`.
 *
 * All seed content is synthetic demo data.
 */
@OptIn(ExperimentalTestApi::class)
class ReadmeScreenshotsGenerator {
    private val tempDirs = mutableListOf<java.nio.file.Path>()

    @AfterTest
    fun cleanUpTempDirs() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
        tempDirs.clear()
    }


    private companion object {
        /** The fictional next release shown on the update card. */
        const val NEXT_VERSION = "26.9.2-tacit.4"
    }

    private val password = DEMO_PASSWORD
    private val outputDir: File =
        System.getProperty("tacit.readmeScreenshots.outputDir")?.let(::File) ?: File(repoRoot(), ".images")

    /** Optional `-PreadmeScreenshots=update,about`: only these PNGs are (over)written. */
    private val only: Set<String>? =
        System.getProperty("tacit.readmeScreenshots.only")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

    private val failures = mutableListOf<String>()
    private val escapeKeyPressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val debugDir: File = File(repoRoot(), "build/readmeScreenshots")

    @Test
    fun `generate README screenshots`() {
        Locale.setDefault(Locale.ENGLISH)
        val dataDir = Files.createTempDirectory("tacit-readme-screenshots").also(tempDirs::add)
        configureScreenshotLogging(dataDir.toOkioPath())
        outputDir.mkdirs()

        tuwunelDocker().use { container ->
            container.start()
            val serverUrl = container.clientServerUrl()
            val http = MatrixHttp(serverUrl)
            val users = seedDemoUsers(http)
            val (tacit, tacit2) = users.tacit to users.tacit2
            val friends = DemoFriends(users.mira, users.ollie, users.pip)

            val app = runBlocking { ScreenshotMessenger.start(dataDir.toOkioPath()) }
            try {
                // Not runDesktopComposeUiTest(): that wrapper ignores its testTimeout and aborts the body after
                // runTest's default 60s (CMP 1.11.1), which froze the scene mid-scenario. Instantiating the harness
                // directly uses its own (unlimited) timeout.
                newScreenshotHarness().runTest {
                    val ui = Ui(this)
                    val i18n = app.messenger.di.get<TacitI18nView>()
                    mainClock.autoAdvance = false
                    setScreenshotContent(app, escapeKeyPressed) {
                        MessengerTheme {
                            // Same overlay as TacitDesktopApp: the update card floats over the client.
                            Box(Modifier.fillMaxSize()) {
                                Client(app.root)
                                val state by app.updates.state.collectAsState()
                                TacitUpdateCard(
                                    i18n = i18n,
                                    version = (state as? TacitUpdateState.ReadyToRestart)?.version,
                                    dismissed = false,
                                    onRestart = app.updates::restartToUpdate,
                                    onWhatsNew = {},
                                    onLater = {},
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 24.dp),
                                )
                            }
                        }
                    }
                    ui.settle(300)
                    scenario(ui, app, serverUrl, tacit, tacit2, friends)
                }
            } finally {
                runBlocking { runCatching { app.multi.closeSuspending() } }
            }
        }
        if (failures.isNotEmpty()) fail("Some screenshots could not be generated:\n - " + failures.joinToString("\n - "))
    }

    private class DemoFriends(val mira: MatrixSession, val ollie: MatrixSession, val pip: MatrixSession)

    private fun scenario(
        ui: Ui,
        app: ScreenshotMessenger,
        serverUrl: String,
        tacit: MatrixSession,
        tacit2: MatrixSession,
        friends: DemoFriends,
    ) {
        ui.await(120_000, "login of ${tacit.userId}") { app.login(serverUrl, "tacit", password) }
        ui.waitForNode("room list showing the admin room", hasText("Admin Room", substring = true))

        // 1. overview: a lived-in DM home - two DMs and a group chat with a short exchange, group chat open
        val (mira, ollie, pip) = Triple(friends.mira, friends.ollie, friends.pip)
        val miraDm = mira.createChat(listOf(tacit.userId), direct = true).also { mira.markDirect(tacit.userId, it) }
        val ollieDm = ollie.createChat(listOf(tacit.userId), direct = true).also { ollie.markDirect(tacit.userId, it) }
        val hike = mira.createChat(listOf(tacit.userId, ollie.userId, pip.userId), name = "Weekend Hike Planning")
        ollie.join(hike)
        pip.join(hike)
        ui.await(60_000, "accept the demo invitations") {
            listOf(miraDm, ollieDm, hike).forEach { roomId ->
                val element = app.roomElement(RoomId(roomId))
                element.isInvite.first { it == true }
                element.acceptInvitation()
                element.isInvite.first { it == false }
            }
        }
        mira.setPresence("online")
        ollie.setPresence("online")
        mira.sendText(miraDm, "Did you see the new trail photos? 📸")
        ollie.sendText(ollieDm, "Thanks for the tips yesterday, that fixed it!")
        // Runs of consecutive messages from one sender (remote and local) exercise message grouping.
        val kickoff = mira.sendText(hike, "Hey all 👋 who's in for the ridge trail on Saturday?")
        mira.sendText(hike, "Forecast says sunny all day ☀️")
        mira.sendText(hike, "I was thinking we start early and do the full loop")
        ollie.sendText(hike, "Count me in 🥾 I'll bring the map")
        pip.sendReply(hike, "Saturday works for me too!", replyTo = kickoff)
        mira.sendText(hike, "Great, 9 o'clock at the trailhead then? 🌄")
        // The local user answers last, so the timeline sits at the bottom with both bubble styles visible.
        ui.await(45_000, "open the group chat and answer") {
            val timeline = app.selectRoom(RoomId(hike))
            delay(1_000)
            with(app) {
                sendMessage(timeline.inputAreaViewModel, "Perfect, see you all there 😊")
                delay(700)
                sendMessage(timeline.inputAreaViewModel, "I'll bring snacks for everyone")
                delay(700)
                sendMessage(timeline.inputAreaViewModel, "And the good camera 📷")
            }
        }
        // Reactions on the local user's message (remote users react).
        val perfectEventId = ollie.findMessageEventId(hike, "see you all there")
        ollie.react(hike, perfectEventId, "🎉")
        pip.react(hike, perfectEventId, "🎉")
        mira.react(hike, perfectEventId, "🥾")
        step("overview") {
            ui.waitForNode("own message in the group chat", hasText("good camera", substring = true))
            ui.settleForCapture() // the lazy list only composes visible rows: anchor it at the end first
            ui.waitForNode("reactions on the own message", hasText("🎉", substring = true))
            ui.settleForCapture()
            ui.capture("overview")
        }

        // 1b. update: the floating card for a staged update over the same chat
        step("update") {
            app.updates.state.value = TacitUpdateState.ReadyToRestart(NEXT_VERSION)
            ui.settleForCapture()
            ui.waitForNode("update card", hasText("Quit and update", substring = true) and hasClickAction())
            ui.settle(600) // let the enter animation finish
            ui.capture("update")
            app.updates.state.value = TacitUpdateState.UpToDate // the card is gone again; About shows "up to date"
            ui.settle(600)
        }

        // 2. invite: tacit2 starts a DM with tacit -> pending invitation in the room list
        val dmRoomId = tacit2.createRoom(
            buildJsonObject {
                put("is_direct", true)
                put("preset", "trusted_private_chat")
                put("invite", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(tacit.userId)) })
            }
        )
        tacit2.markDirect(tacit.userId, dmRoomId)
        val dmRoom = RoomId(dmRoomId)
        step("invite") {
            ui.await(30_000, "DM invitation to arrive") { app.roomElement(dmRoom).isInvite.first { it == true } }
            ui.waitForNode("pending invitation from tacit2", hasText("tacit2", substring = true))
            ui.settleForCapture()
            ui.capture("invite")
            // Badge check (no screenshot): the invite holds a notification that "mark all as read" reports.
            ui.await(30_000, "pending invite in the badge and the mark-all-as-read plan") {
                app.messenger.notificationCount.first { it >= 1 }
                val markAllAsRead = app.messenger.di.get<TacitMarkAllAsRead>()
                while (dmRoom !in markAllAsRead.plan(app.matrixClient()).pendingInvites) delay(250)
            }
        }

        // 3. chatting: accept, tacit2 says hi, tacit answers
        ui.await(30_000, "accept DM invitation") {
            val element = app.roomElement(dmRoom)
            element.acceptInvitation()
            element.isInvite.first { it == false }
        }
        tacit2.setPresence("online")
        tacit2.sendText(dmRoomId, "Howdy 👋")
        tacit2.sendText(dmRoomId, "Just installed Tacit")
        tacit2.sendText(dmRoomId, "Looks neat so far!")
        step("chatting") {
            ui.await(30_000, "open DM and send a message") {
                val timeline = app.selectRoom(dmRoom)
                delay(1_000)
                with(app) { sendMessage(timeline.inputAreaViewModel, "Let's chat 😊") }
            }
            ui.waitForNode("own message in the timeline", hasText("Let's chat 😊", substring = true))
            ui.waitForNode("tacit2's message in the timeline", hasText("Howdy", substring = true))
            tacit2.react(dmRoomId, tacit2.findMessageEventId(dmRoomId, "Let's chat"), "👍")
            ui.settleForCapture()
            ui.waitForNode("reaction in the DM", hasText("👍", substring = true))
            ui.settleForCapture()
            ui.capture("chatting")
        }

        // 4. emoji_picker: typing a shortcode shows the emoji suggestions
        step("emoji_picker") {
            ui.settleForCapture()
            ui.typeInto(messageInputMatcher(), ":part")
            ui.waitForNode("emoji suggestions", hasText(":party_face:", substring = true))
            ui.settle(500)
            ui.capture("emoji_picker")
            ui.await(10_000, "clear composer") { app.timeline(dmRoom).inputAreaViewModel.textField.update("") }
            ui.settle(300)
        }

        // 5. guild_create: the real dialog, filled in, before creating the guild through it
        step("guild_create") {
            ui.settleForCapture()
            ui.click(hasText("Howdy", substring = true)) // moves focus out of the composer (system events are collapsed)
            ui.parkPointer()
            ui.activate(hasContentDescription("Create guild")) // no pointer hover: the button has a tooltip
            ui.waitForNode("create guild dialog", hasText("Create Guild"))
            ui.typeInto(hasText("Guild name"), "Adventure")
            ui.typeInto(hasText("Guild topic (optional)"), "This is my guild for adventurers :)")
            ui.settle(500)
            ui.capture("guild_create")
        }
        ui.click(hasText("create", ignoreCase = true) and hasClickAction())
        val spaceRoom = ui.await(60_000, "guild space to be created") { app.findSpaceRoom() }
        ui.awaitGuildLabel(app, spaceRoom)

        // 6. guild_ui: the guild with its #general room open
        val generalRoom = ui.await(60_000, "#general room of the guild") { app.spaceChildren(spaceRoom).first() }
        ui.click(hasText("ADV") and hasClickAction())
        ui.await(30_000, "open #general") { app.selectRoom(generalRoom) }
        ui.waitForNode("#general header", hasText("General discussion", substring = true))
        step("guild_ui") {
            ui.settleForCapture()
            ui.capture("guild_ui")
        }

        // 7. guild_invite: the real invite dialog with a user directory hit
        step("guild_invite") {
            ui.settleForCapture()
            // moves focus out of the composer and expands the collapsed system events behind the dialog
            ui.click(hasText("room events", substring = true) and hasClickAction())
            ui.settle(500)
            ui.parkPointer()
            ui.activate(hasContentDescription("Invite members"))
            ui.waitForNode("invite dialog", hasText("Invite to Guild"))
            ui.typeInto(hasText("Matrix user ID"), "tac")
            ui.waitForNode("user directory result", hasText(tacit2.userId, substring = true) and hasClickAction())
            ui.settle(500)
            ui.capture("guild_invite")
            ui.click(hasText(tacit2.userId, substring = true) and hasClickAction())
            ui.click(hasText("Send Invite", ignoreCase = true) and hasClickAction())
            ui.settle(500)
        }
        // tacit2 accepts and enters #general through the restricted join rule
        tacit2.join(spaceRoom.full)
        tacit2.spaceChildren(spaceRoom.full).forEach { tacit2.join(it) }

        // 8. member_list: members pane of #general
        step("member_list") {
            ui.click(hasContentDescription("Toggle members pane"))
            ui.waitForNode("tacit2 in the members pane", hasText(tacit2.userId, substring = true), timeoutMillis = 60_000)
            ui.settleForCapture()
            ui.capture("member_list")
        }

        // 9. about: the about page (members pane still open, as in the original screenshot)
        step("about") {
            ui.click(hasContentDescription("About Tacit"))
            ui.waitForNode("about page", hasText("Project repository"))
            // The header's back button takes focus on first render and Material3 shows its tooltip for a focused
            // anchor; a click into the timeline clears the focus like a user would.
            ui.click(hasText("has created the group", substring = true))
            ui.settleForCapture()
            ui.capture("about")
            ui.await(10_000, "close about page") {
                app.main().roomListRouterStack.waitFor(RoomListRouter.Wrapper.AppInfo::class).viewModel.close()
            }
            ui.click(hasContentDescription("Toggle members pane"))
        }

        // 10. spoiler: back in the DM, a spoiler message and the slash command suggestions
        step("spoiler") {
            ui.await(30_000, "send spoiler message") {
                app.selectGuild(null)
                val timeline = app.selectRoom(dmRoom)
                delay(500)
                with(app) { sendMessage(timeline.inputAreaViewModel, "/spoiler Hidden features are waiting for you :D") }
            }
            ui.waitForNode("DM home", hasText("All DMs"))
            // Wait until the server has the spoiler (so the outbox placeholder is replaced by the real event).
            ui.await(30_000, "spoiler message to reach the server") {
                while (tacit2.lastMessageBody(dmRoomId)?.contains("Hidden features") != true) delay(250)
            }
            ui.waitForNode("spoiler message", hasText("Hidden features", substring = true), timeoutMillis = 30_000)
            ui.settleForCapture()
            ui.typeInto(messageInputMatcher(), "/spo")
            ui.waitForNode("slash command suggestions", hasText("/spoiler", substring = true))
            ui.settle(500)
            ui.capture("spoiler")
        }
    }

    /**
     * The guild pill shows the space's explicit name ("ADV" for "Adventure"); see [ScreenshotMessenger.fillTimelineGaps]
     * for why that needs a nudge for a space created in this session. If it still does not resolve, re-send the name
     * state event once (a regular Matrix operation), which makes the client recompute it.
     */
    private fun Ui.awaitGuildLabel(app: ScreenshotMessenger, spaceRoom: RoomId) {
        val pill = hasText("ADV") and hasClickAction()
        val resolved = runCatching { waitForNode("the new guild in the guild rail", pill, timeoutMillis = 45_000) }.isSuccess
        if (resolved) return
        println("SCREENSHOT guild name not resolved yet; rooms:\n" + runBlocking { app.describeAllRooms() })
        runBlocking { app.resendRoomName(spaceRoom, "Adventure") }
        waitForNode("the new guild in the guild rail (after re-sending the room name)", pill, timeoutMillis = 60_000)
    }

    private fun messageInputMatcher(): SemanticsMatcher = hasSetTextAction() and hasText("Your message", substring = true)

    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { failure ->
            failures += "$name: ${failure::class.simpleName}: ${failure.message?.lineSequence()?.first()}"
            println("SCREENSHOT '$name' FAILED: $failure")
            failure.printStackTrace()
        }
    }

    /** Small driver around the Compose test harness: pumps frames manually so infinite animations cannot stall it. */
    private inner class Ui(private val test: ComposeUiTest) {
        fun pump() {
            test.mainClock.advanceTimeByFrame()
            test.waitForIdle()
        }

        /** Renders frames for [realMillis] wall-clock time so background (network/sync) state lands in the UI. */
        fun settle(realMillis: Long) {
            val deadline = System.currentTimeMillis() + realMillis
            while (System.currentTimeMillis() < deadline) {
                pump()
                Thread.sleep(16)
            }
        }

        /** Runs [block] on a background dispatcher while keeping the UI rendering. */
        fun <T> await(timeoutMillis: Long, what: String, block: suspend CoroutineScope.() -> T): T {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val deferred = scope.async(block = block)
            val deadline = System.currentTimeMillis() + timeoutMillis
            try {
                while (!deferred.isCompleted) {
                    pump()
                    Thread.sleep(16)
                    if (System.currentTimeMillis() > deadline) {
                        deferred.cancel()
                        debugCapture(what)
                        throw AssertionError("timed out after ${timeoutMillis}ms while waiting for: $what")
                    }
                }
                return runBlocking { deferred.await() }
            } finally {
                scope.cancel()
            }
        }

        fun waitForNode(what: String, matcher: SemanticsMatcher, timeoutMillis: Long = 30_000) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (true) {
                pump()
                if (test.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
                if (System.currentTimeMillis() > deadline) {
                    debugCapture(what)
                    throw AssertionError("timed out after ${timeoutMillis}ms waiting for: $what")
                }
                Thread.sleep(50)
            }
        }

        /**
         * Brings the screen into a stable state before a capture: parks the mouse pointer on a neutral spot and
         * dismisses tooltips (hover tooltips of the last click would otherwise leak into the picture), then - as the
         * very last action - anchors every lazy list at its start, so the (reversed) timeline sits exactly on its
         * newest item and the "jump to the end" chevron is gone. Sync activity that arrived between steps, or the
         * padded scroll the view model's own jump-to-end performs, would otherwise leave the list a few px off.
         */
        fun settleForCapture() {
            settle(300)
            parkPointer()
            // Hover tooltips are popups; a modal dialog above the hovered button swallows the pointer "exit", so
            // dismiss them the way the app does on Escape and wait until no popup is left.
            val popupDeadline = System.currentTimeMillis() + 6_000
            do {
                escapeKeyPressed.tryEmit(Unit)
                settle(600)
            } while (test.onAllNodes(isPopup()).fetchSemanticsNodes().isNotEmpty() && System.currentTimeMillis() < popupDeadline)
            println("SCREENSHOT settle: popups=${test.onAllNodes(isPopup()).fetchSemanticsNodes().size}")
            settle(600)
            val chevron = hasContentDescription("Jump to the end")
            val spinner = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)
            fun unsettled() = test.onAllNodes(chevron or spinner).fetchSemanticsNodes().isNotEmpty()
            val deadline = System.currentTimeMillis() + 10_000
            do {
                pinListsToEnd()
                settle(500)
            } while (unsettled() && System.currentTimeMillis() < deadline)
            println(
                "SCREENSHOT settle: chevron=${test.onAllNodes(chevron).fetchSemanticsNodes().size} " +
                    "spinners=${test.onAllNodes(spinner).fetchSemanticsNodes().size}"
            )
        }

        /** `scrollToItem(0)` on every lazy list: the (reversed) timeline lands exactly on its newest item. */
        private fun pinListsToEnd() {
            test.onAllNodes(hasScrollToIndexAction()).pinToStart()
            pump()
        }

        fun parkPointer() {
            runCatching {
                test.parkPointerOnFooter()
            }.onFailure { println("SCREENSHOT settle: park pointer failed $it") }
            pump()
        }

        /**
         * Triggers a control's click action without moving the pointer onto it. Used for buttons that open a modal
         * dialog: a real click would leave the button hovered underneath the dialog (the dialog swallows the
         * pointer "exit"), so its hover tooltip would show up in the capture.
         */
        fun activate(matcher: SemanticsMatcher) {
            test.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
            settle(200)
        }

        fun click(matcher: SemanticsMatcher) {
            test.onAllNodes(matcher).onFirst().performClick()
            settle(200)
        }

        fun typeInto(matcher: SemanticsMatcher, text: String) {
            val node = test.onAllNodes(matcher).onFirst()
            node.performClick()
            pump()
            node.performTextInput(text)
            settle(200)
        }

        fun capture(name: String) {
            val popups = test.onAllNodes(isPopup()).fetchSemanticsNodes().size
            if (popups > 0) println("SCREENSHOT WARNING: $name captured with $popups popup(s) open")
            if (only != null && name !in only) return
            val out = File(outputDir, "$name.png")
            out.writeBytes(ScreenshotFrame.toPng(captureRoot()))
            println("SCREENSHOT wrote ${out.absolutePath}")
        }

        /** Raw capture of whatever is on screen when something goes wrong, for diagnosing failed runs. */
        fun debugCapture(what: String) {
            runCatching {
                debugDir.mkdirs()
                val out = File(debugDir, "failure-" + what.replace(Regex("[^A-Za-z0-9]+"), "_").take(60) + ".png")
                out.writeBytes(ScreenshotFrame.toPng(captureRoot()))
                println("SCREENSHOT debug capture: ${out.absolutePath}")
            }.onFailure { println("SCREENSHOT debug capture failed: $it") }
        }

        private fun captureRoot(): androidx.compose.ui.graphics.ImageBitmap {
            pump()
            val roots = test.onAllNodes(isRoot())
            val rootCount = roots.fetchSemanticsNodes().size
            // With a Dialog open there are two roots; either one yields the fully composited frame.
            return roots[rootCount - 1].captureToImage()
        }
    }

    private fun repoRoot(): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
}
