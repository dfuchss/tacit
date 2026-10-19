package org.fuchss.matrix.tacit.readmeshots

import androidx.compose.ui.test.ExperimentalTestApi
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

/**
 * Dev-only capture of formatted blockquotes (`<blockquote>`, markdown `> …`) next to reply quotes, in a room of its
 * own (the README seed has no blockquote and stays untouched). Skipped unless `TACIT_QUOTE_SCREENSHOTS_DIR` is set;
 * never writes into `.images/`. Run with
 * `TACIT_QUOTE_SCREENSHOTS_DIR=/some/dir ./gradlew generateReadmeScreenshots --tests '*QuoteScreenshots*'`
 * (optional file name prefix: `TACIT_QUOTE_SCREENSHOTS_PREFIX`).
 */
@OptIn(ExperimentalTestApi::class)
class QuoteScreenshots : DevScreenshotTest("QUOTE", "tacit-quote-screenshots") {
    @Test
    fun `capture blockquote screenshots`() {
        runDevScreenshots(seed = ::seedQuoteRoom) { session ->
            val app = session.app
            settle(300)
            await("login") { app.login(session.serverUrl, "tacit", DEMO_PASSWORD) }
            await("open the quote room") {
                app.selectRoom(RoomId(session.seeded)).also { delay(1_000) }
            }
            waitForText("Couldn't agree more")
            settleForCapture()
            capture("quotes")
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
