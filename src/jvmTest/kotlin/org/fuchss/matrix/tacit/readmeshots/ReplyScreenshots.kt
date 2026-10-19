package org.fuchss.matrix.tacit.readmeshots

import androidx.compose.ui.test.ExperimentalTestApi
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.delay
import kotlin.test.Test

/**
 * Dev-only capture of the reply UI (quote in the timeline, reply bar in the composer) against the seeded demo
 * homeserver. Skipped unless `TACIT_REPLY_SCREENSHOTS_DIR` is set; never writes into `.images/`. Run with
 * `TACIT_REPLY_SCREENSHOTS_DIR=/some/dir ./gradlew generateReadmeScreenshots --tests '*ReplyScreenshots*'`.
 */
@OptIn(ExperimentalTestApi::class)
class ReplyScreenshots : DevScreenshotTest("REPLY", "tacit-reply-screenshots") {
    @Test
    fun `capture reply screenshots`() {
        runDevScreenshots(
            seed = { users ->
                val hike = seedDemoConversations(users)
                hike to users.mira.findMessageEventId(hike, "ridge trail")
            },
        ) { session ->
            val (hike, kickoff) = session.seeded
            val app = session.app

            settle(300)
            await("login") { app.login(session.serverUrl, "tacit", DEMO_PASSWORD) }
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
    }
}
