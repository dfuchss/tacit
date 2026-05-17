package org.fuchss.matrix.tacit.settings

import de.connect2x.trixnity.messenger.MatrixMessengerSettings
import de.connect2x.trixnity.messenger.settings.MutableSettings
import kotlinx.serialization.json.*

private const val TACIT_KEY = "tacit"
private const val DESKTOP_KEY = "desktop"
private const val WINDOW_CLOSE_BEHAVIOR_KEY = "windowCloseBehavior"

enum class TacitWindowCloseBehavior {
    BACKGROUND,
    EXIT,
}

fun readTacitWindowCloseBehavior(settingsMap: Map<String, JsonElement>): TacitWindowCloseBehavior {
    val tacit = settingsMap[TACIT_KEY] as? JsonObject ?: return TacitWindowCloseBehavior.BACKGROUND
    val desktop = tacit[DESKTOP_KEY] as? JsonObject ?: return TacitWindowCloseBehavior.BACKGROUND
    return when (desktop[WINDOW_CLOSE_BEHAVIOR_KEY]?.jsonPrimitive?.contentOrNull?.uppercase()) {
        TacitWindowCloseBehavior.EXIT.name -> TacitWindowCloseBehavior.EXIT
        else -> TacitWindowCloseBehavior.BACKGROUND
    }
}

fun MutableSettings<MatrixMessengerSettings>.writeTacitWindowCloseBehavior(
    behavior: TacitWindowCloseBehavior,
) {
    val currentTacit = this[TACIT_KEY] as? JsonObject
    val currentDesktop = currentTacit?.get(DESKTOP_KEY) as? JsonObject
    val newDesktop = JsonObject(
        (currentDesktop?.toMap().orEmpty()) + (WINDOW_CLOSE_BEHAVIOR_KEY to JsonPrimitive(behavior.name))
    )
    val newTacit = JsonObject((currentTacit?.toMap().orEmpty()) + (DESKTOP_KEY to newDesktop))
    this[TACIT_KEY] = newTacit
}
