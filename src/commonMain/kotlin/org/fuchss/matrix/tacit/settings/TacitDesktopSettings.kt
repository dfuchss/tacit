package org.fuchss.matrix.tacit.settings

import de.connect2x.trixnity.messenger.MatrixMessengerSettings
import de.connect2x.trixnity.messenger.settings.MutableSettings
import kotlinx.serialization.json.*

private const val TACIT_KEY = "tacit"
private const val DESKTOP_KEY = "desktop"
private const val WINDOW_CLOSE_BEHAVIOR_KEY = "windowCloseBehavior"
private const val ROOM_LIST_WIDTH_DP_KEY = "roomListWidthDp"

enum class TacitWindowCloseBehavior {
    BACKGROUND,
    EXIT,
}

private fun desktopSettings(settingsMap: Map<String, JsonElement>): JsonObject? {
    val tacit = settingsMap[TACIT_KEY] as? JsonObject ?: return null
    return tacit[DESKTOP_KEY] as? JsonObject
}

private fun MutableSettings<MatrixMessengerSettings>.updateDesktopSettings(
    key: String,
    value: JsonElement,
) {
    val currentTacit = this[TACIT_KEY] as? JsonObject
    val currentDesktop = currentTacit?.get(DESKTOP_KEY) as? JsonObject
    val newDesktop = JsonObject((currentDesktop?.toMap().orEmpty()) + (key to value))
    val newTacit = JsonObject((currentTacit?.toMap().orEmpty()) + (DESKTOP_KEY to newDesktop))
    this[TACIT_KEY] = newTacit
}

fun readTacitWindowCloseBehavior(settingsMap: Map<String, JsonElement>): TacitWindowCloseBehavior {
    val desktop = desktopSettings(settingsMap) ?: return TacitWindowCloseBehavior.BACKGROUND
    return when (desktop[WINDOW_CLOSE_BEHAVIOR_KEY]?.jsonPrimitive?.contentOrNull?.uppercase()) {
        TacitWindowCloseBehavior.EXIT.name -> TacitWindowCloseBehavior.EXIT
        else -> TacitWindowCloseBehavior.BACKGROUND
    }
}

fun MutableSettings<MatrixMessengerSettings>.writeTacitWindowCloseBehavior(
    behavior: TacitWindowCloseBehavior,
) = updateDesktopSettings(WINDOW_CLOSE_BEHAVIOR_KEY, JsonPrimitive(behavior.name))

/**
 * The persisted absolute width (in dp) of the room list column of the two pane layout,
 * or `null` when the user never moved the splitter.
 */
fun readTacitRoomListWidthDp(settingsMap: Map<String, JsonElement>): Float? {
    val desktop = desktopSettings(settingsMap) ?: return null
    return desktop[ROOM_LIST_WIDTH_DP_KEY]?.jsonPrimitive?.floatOrNull?.takeIf { it.isFinite() && it > 0f }
}

fun MutableSettings<MatrixMessengerSettings>.writeTacitRoomListWidthDp(
    widthDp: Float,
) = updateDesktopSettings(ROOM_LIST_WIDTH_DP_KEY, JsonPrimitive(widthDp))
