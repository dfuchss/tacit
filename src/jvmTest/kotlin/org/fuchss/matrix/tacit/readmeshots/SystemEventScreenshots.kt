package org.fuchss.matrix.tacit.readmeshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.fuchss.matrix.tacit.views.room.timeline.TacitSystemEventGroupTestTag
import kotlin.test.Test

/**
 * Dev-only capture of the collapsed system-event group in a fresh DM (the same scenario as the README's
 * `chatting.png`): tacit2 opens a DM, tacit accepts and answers. Writes `collapsed.png` and, when a group header
 * is on screen, `expanded.png` after clicking it; then the same for an encrypted DM, whose own invite carries the
 * "previous messages cannot be decrypted" notice (`encrypted-collapsed.png`, `encrypted-expanded.png`). Skipped unless `TACIT_SYSEVENT_SCREENSHOTS_DIR` is set; never
 * writes into `.images/`. Run with
 * `TACIT_SYSEVENT_SCREENSHOTS_DIR=/some/dir ./gradlew generateReadmeScreenshots --tests '*SystemEventScreenshots*'`
 * (optional file name prefix: `TACIT_SYSEVENT_SCREENSHOTS_PREFIX`).
 */
@OptIn(ExperimentalTestApi::class)
class SystemEventScreenshots : DevScreenshotTest("SYSEVENT", "tacit-sysevent-screenshots") {
    @Test
    fun `capture system event group screenshots`() {
        runDevScreenshots(seed = {}) { session ->
            val app = session.app
            val (tacit, tacit2) = session.users.tacit to session.users.tacit2

            settle(300)
            await("login") { app.login(session.serverUrl, "tacit", DEMO_PASSWORD) }

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
            settleForCapture(dismissPopups = true)
            capture("collapsed")

            val header = hasTestTag(TacitSystemEventGroupTestTag)
            fun captureExpanded(name: String) {
                if (onAllNodes(header).fetchSemanticsNodes().isNotEmpty()) {
                    onAllNodes(header).onFirst().performClick()
                    settle(1_000)
                    settleForCapture(dismissPopups = true)
                    capture(name)
                } else {
                    println("SCREENSHOT no system event group on screen; skipped $name.png")
                }
            }
            captureExpanded("expanded")

            // An encrypted DM: the own invite carries the "previous messages cannot be
            // decrypted" notice. Only tacit writes here (the seeded users cannot encrypt).
            val encryptedRoomId = tacit2.createRoom(
                buildJsonObject {
                    put("is_direct", true)
                    put("preset", "trusted_private_chat")
                    put("invite", buildJsonArray { add(JsonPrimitive(tacit.userId)) })
                    put(
                        "initial_state",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "m.room.encryption")
                                    put("state_key", "")
                                    put("content", buildJsonObject { put("algorithm", "m.megolm.v1.aes-sha2") })
                                }
                            )
                        }
                    )
                }
            )
            val encryptedRoom = RoomId(encryptedRoomId)
            await("accept encrypted DM invitation") {
                val element = app.roomElement(encryptedRoom)
                element.isInvite.first { it == true }
                element.acceptInvitation()
                element.isInvite.first { it == false }
            }
            await("open encrypted DM and send a message") {
                val timeline = app.selectRoom(encryptedRoom)
                delay(1_000)
                with(app) { sendMessage(timeline.inputAreaViewModel, "Is this encrypted? 🔒") }
            }
            waitFor("own encrypted message", hasText("Is this encrypted?", substring = true))
            settleForCapture(dismissPopups = true)
            capture("encrypted-collapsed")
            captureExpanded("encrypted-expanded")
        }
    }
}
